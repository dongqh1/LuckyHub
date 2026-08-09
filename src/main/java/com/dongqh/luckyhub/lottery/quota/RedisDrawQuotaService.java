package com.dongqh.luckyhub.lottery.quota;

import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.lottery.config.LotteryProperties;
import com.dongqh.luckyhub.lottery.enums.LotteryErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class RedisDrawQuotaService implements DrawQuotaService {

    private static final DateTimeFormatter DRAW_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final long RESERVE_DUPLICATE = 0L;
    private static final long RESERVE_CREATED = 1L;
    private static final long RESERVE_INSUFFICIENT = 2L;
    private static final long RESERVE_CONFLICT = 3L;

    private static final DefaultRedisScript<List> RESERVE_SCRIPT = script(
            "redis/lottery/reserve_draw_quota.lua", List.class);
    private static final DefaultRedisScript<Long> CONFIRM_SCRIPT = script(
            "redis/lottery/confirm_draw_quota.lua", Long.class);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = script(
            "redis/lottery/release_draw_quota.lua", Long.class);

    private final StringRedisTemplate redisTemplate;
    private final LotteryProperties properties;
    private final Clock clock;

    @Autowired
    public RedisDrawQuotaService(StringRedisTemplate redisTemplate, LotteryProperties properties) {
        this(redisTemplate, properties, Clock.system(properties.zoneId()));
    }

    RedisDrawQuotaService(StringRedisTemplate redisTemplate, LotteryProperties properties, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public QuotaReservationResult reserve(QuotaReservationRequest request) {
        validate(request);
        LocalDate drawDate = request.drawDate() == null
                ? LocalDate.now(clock.withZone(properties.zoneId())) : request.drawDate();
        Instant now = clock.instant();
        long expiresAt = drawDate.atStartOfDay(properties.zoneId()).toInstant()
                .plus(properties.reservationRetention()).toEpochMilli();
        List<String> keys = List.of(
                DrawQuotaKeys.quota(request.activityId(), request.userId(), drawDate),
                DrawQuotaKeys.reservation(request.requestId()),
                DrawQuotaKeys.reservationTimeouts()
        );
        try {
            List<?> response = redisTemplate.execute(
                    RESERVE_SCRIPT,
                    keys,
                    request.requestId(),
                    Long.toString(request.activityId()),
                    Long.toString(request.userId()),
                    Integer.toString(request.drawCount()),
                    Long.toString(request.dailyLimit()),
                    DRAW_DATE.format(drawDate),
                    Long.toString(now.toEpochMilli()),
                    Long.toString(now.plus(properties.processingTimeout()).toEpochMilli()),
                    Long.toString(expiresAt)
            );
            // The script compares requestId/userId/activityId/drawCount only. dailyLimit applies
            // only when creating a reservation, so policy changes cannot break an existing retry.
            return mapReservationResponse(request, drawDate, response);
        } catch (BusinessException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new BusinessException(LotteryErrorCode.DRAW_QUOTA_UNAVAILABLE);
        }
    }

    @Override
    public void confirm(String requestId) {
        requireRequestId(requestId);
        try {
            Long result = redisTemplate.execute(
                    CONFIRM_SCRIPT,
                    List.of(DrawQuotaKeys.reservation(requestId), DrawQuotaKeys.reservationTimeouts()),
                    requestId
            );
            requireValidTransitionResult(result);
        } catch (RuntimeException error) {
            throw new BusinessException(LotteryErrorCode.DRAW_QUOTA_UNAVAILABLE);
        }
    }

    @Override
    public void release(String requestId) {
        requireRequestId(requestId);
        String reservationKey = DrawQuotaKeys.reservation(requestId);
        try {
            List<Object> fields = redisTemplate.opsForHash().multiGet(
                    reservationKey, List.of("activityId", "userId", "drawDate"));
            if (fields.size() != 3 || fields.stream().anyMatch(value -> value == null)) {
                Long result = redisTemplate.execute(
                        RELEASE_SCRIPT,
                        List.of(reservationKey, DrawQuotaKeys.reservationTimeouts(), ""),
                        requestId, "", "", ""
                );
                requireValidTransitionResult(result);
                return;
            }
            long activityId = Long.parseLong(fields.get(0).toString());
            long userId = Long.parseLong(fields.get(1).toString());
            LocalDate drawDate = LocalDate.parse(fields.get(2).toString(), DRAW_DATE);
            Long result = redisTemplate.execute(
                    RELEASE_SCRIPT,
                    List.of(
                            reservationKey,
                            DrawQuotaKeys.reservationTimeouts(),
                            DrawQuotaKeys.quota(activityId, userId, drawDate)
                    ),
                    requestId,
                    Long.toString(activityId),
                    Long.toString(userId),
                    DRAW_DATE.format(drawDate)
            );
            requireValidTransitionResult(result);
        } catch (RuntimeException error) {
            throw new BusinessException(LotteryErrorCode.DRAW_QUOTA_UNAVAILABLE);
        }
    }

    @Override
    public void removeTimeout(String requestId) {
        requireRequestId(requestId);
        try {
            redisTemplate.opsForZSet().remove(DrawQuotaKeys.reservationTimeouts(), requestId);
        } catch (RuntimeException error) {
            throw new BusinessException(LotteryErrorCode.DRAW_QUOTA_UNAVAILABLE);
        }
    }

    private QuotaReservationResult mapReservationResponse(
            QuotaReservationRequest request,
            LocalDate drawDate,
            List<?> response
    ) {
        if (response == null || response.isEmpty()) {
            throw new BusinessException(LotteryErrorCode.DRAW_QUOTA_UNAVAILABLE);
        }
        long code = Long.parseLong(response.get(0).toString());
        if (code == RESERVE_INSUFFICIENT) {
            LotteryErrorCode errorCode = request.drawCount() == 10
                    ? LotteryErrorCode.TEN_DRAW_QUOTA_EXCEEDED
                    : LotteryErrorCode.DAILY_QUOTA_EXCEEDED;
            throw new BusinessException(errorCode);
        }
        if (code == RESERVE_CONFLICT) {
            throw new BusinessException(LotteryErrorCode.IDEMPOTENCY_CONFLICT);
        }
        if (code != RESERVE_CREATED && code != RESERVE_DUPLICATE) {
            throw new BusinessException(LotteryErrorCode.DRAW_QUOTA_UNAVAILABLE);
        }
        ReservationStatus status = response.size() > 1
                ? ReservationStatus.valueOf(response.get(1).toString())
                : ReservationStatus.RESERVED;
        LocalDate resultDrawDate = response.size() > 2
                ? LocalDate.parse(response.get(2).toString(), DRAW_DATE)
                : drawDate;
        int resultDrawCount = response.size() > 3
                ? Integer.parseInt(response.get(3).toString())
                : request.drawCount();
        return new QuotaReservationResult(
                request.requestId(), status, resultDrawDate, resultDrawCount, code == RESERVE_DUPLICATE);
    }

    private void validate(QuotaReservationRequest request) {
        if (request == null || !StringUtils.hasText(request.requestId())
                || request.requestId().length() > 64
                || request.activityId() <= 0 || request.userId() <= 0
                || (request.drawCount() != 1 && request.drawCount() != 10)
                || request.dailyLimit() < 0) {
            throw new BusinessException(LotteryErrorCode.DRAW_PARAMETER_INVALID);
        }
    }

    private void requireRequestId(String requestId) {
        if (!StringUtils.hasText(requestId) || requestId.length() > 64) {
            throw new BusinessException(LotteryErrorCode.DRAW_PARAMETER_INVALID);
        }
    }

    private void requireValidTransitionResult(Long result) {
        if (result == null || (result != 0L && result != 1L)) {
            throw new BusinessException(LotteryErrorCode.DRAW_QUOTA_UNAVAILABLE);
        }
    }

    private static <T> DefaultRedisScript<T> script(String path, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(resultType);
        return script;
    }
}
