package com.dongqh.luckyhub.rbac.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PermissionCacheService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    PermissionCacheService.class
            );

    private static final String KEY_PREFIX =
            "rbac:permissions:user:";

    private static final Duration CACHE_TTL =
            Duration.ofMinutes(10);

    private static final TypeReference<List<String>>
            PERMISSION_LIST_TYPE =
            new TypeReference<>() {
            };

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public PermissionCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询权限缓存。
     *
     * Optional.empty() 表示缓存不存在或 Redis 不可用。
     * Optional.of(emptySet()) 表示用户权限确实为空。
     */
    public Optional<Set<String>> get(Long userId) {
        String key = buildKey(userId);

        try {
            String json = redisTemplate
                    .opsForValue()
                    .get(key);

            if (json == null) {
                return Optional.empty();
            }

            List<String> permissionCodes =
                    objectMapper.readValue(
                            json,
                            PERMISSION_LIST_TYPE
                    );

            if (permissionCodes == null) {
                deleteQuietly(key);
                return Optional.empty();
            }

            return Optional.of(
                    permissionCodes
                            .stream()
                            .collect(
                                    Collectors.toCollection(
                                            LinkedHashSet::new
                                    )
                            )
            );
        } catch (JacksonException exception) {
            /*
             * 缓存内容损坏时删除缓存，
             * 后续回源数据库重新生成。
             */
            log.warn(
                    "Invalid permission cache, userId={}",
                    userId,
                    exception
            );

            deleteQuietly(key);
            return Optional.empty();
        } catch (DataAccessException exception) {
            /*
             * Redis 故障不能阻断权限校验，
             * 返回缓存未命中，让 Service 查询数据库。
             */
            log.warn(
                    "Failed to read permission cache, userId={}",
                    userId,
                    exception
            );

            return Optional.empty();
        }
    }

    /**
     * 写入权限缓存。
     *
     * 空权限会写成 []，避免无权限用户每次都查询数据库。
     */
    public void put(
            Long userId,
            Set<String> permissionCodes
    ) {
        try {
            List<String> sortedCodes =
                    permissionCodes.stream()
                            .sorted()
                            .toList();

            String json =
                    objectMapper.writeValueAsString(
                            sortedCodes
                    );

            redisTemplate.opsForValue().set(
                    buildKey(userId),
                    json,
                    CACHE_TTL
            );
        } catch (JacksonException exception) {
            log.warn(
                    "Failed to serialize permission cache, "
                            + "userId={}",
                    userId,
                    exception
            );
        } catch (DataAccessException exception) {
            /*
             * Redis 写入失败时不影响业务请求，
             * 下一次请求继续查询数据库。
             */
            log.warn(
                    "Failed to write permission cache, "
                            + "userId={}",
                    userId,
                    exception
            );
        }
    }

    public void evict(Long userId) {
        evict(Set.of(userId));
    }

    public void evict(Collection<Long> userIds) {
        List<String> keys = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(this::buildKey)
                .toList();

        if (keys.isEmpty()) {
            return;
        }

        try {
            redisTemplate.delete(keys);
        } catch (DataAccessException exception) {
            /*
             * 数据库修改已经可能成功，
             * 缓存删除失败只能等待 TTL 过期，
             * 因此必须记录日志。
             */
            log.error(
                    "Failed to evict permission caches, "
                            + "userIds={}",
                    userIds,
                    exception
            );
        }
    }

    /**
     * 立即删除一次，并在数据库事务提交后再次删除。
     *
     * 第一次删除：减少旧权限继续被读取的时间。
     * 提交后删除：防止并发请求在事务提交前把旧权限重新写入缓存。
     */
    public void evictNowAndAfterCommit(
            Collection<Long> userIds
    ) {
        Set<Long> copiedUserIds =
                userIds.stream()
                        .filter(Objects::nonNull)
                        .collect(
                                Collectors.toUnmodifiableSet()
                        );

        if (copiedUserIds.isEmpty()) {
            return;
        }

        evict(copiedUserIds);

        if (TransactionSynchronizationManager
                .isActualTransactionActive()
                && TransactionSynchronizationManager
                .isSynchronizationActive()) {

            TransactionSynchronizationManager
                    .registerSynchronization(
                            new TransactionSynchronization() {
                                @Override
                                public void afterCommit() {
                                    evict(copiedUserIds);
                                }
                            }
                    );
        }
    }

    private String buildKey(Long userId) {
        return KEY_PREFIX + userId;
    }

    private void deleteQuietly(String key) {
        try {
            redisTemplate.delete(key);
        } catch (DataAccessException exception) {
            log.warn(
                    "Failed to delete invalid permission cache, "
                            + "key={}",
                    key,
                    exception
            );
        }
    }
}
