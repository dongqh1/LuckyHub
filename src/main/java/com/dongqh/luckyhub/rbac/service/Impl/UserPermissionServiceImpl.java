package com.dongqh.luckyhub.rbac.service.Impl;

import com.dongqh.luckyhub.common.exception.NotFoundException;
import com.dongqh.luckyhub.rbac.cache.PermissionCacheService;
import com.dongqh.luckyhub.rbac.entity.SysPermission;
import com.dongqh.luckyhub.rbac.entity.SysUser;
import com.dongqh.luckyhub.rbac.mapper.SysPermissionMapper;
import com.dongqh.luckyhub.rbac.mapper.SysUserMapper;
import com.dongqh.luckyhub.rbac.service.UserPermissionService;
import com.dongqh.luckyhub.rbac.vo.PermissionView;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserPermissionServiceImpl implements UserPermissionService {

    private final SysUserMapper userMapper;
    private final SysPermissionMapper permissionMapper;
    private final PermissionCacheService permissionCacheService;


    public UserPermissionServiceImpl(
            SysUserMapper userMapper,
            SysPermissionMapper permissionMapper,
            PermissionCacheService permissionCacheService
    ) {
        this.userMapper = userMapper;
        this.permissionMapper = permissionMapper;
        this.permissionCacheService = permissionCacheService;
    }

    @Override
    public List<PermissionView> listEffectivePermissions(Long userId) {
        requireUser(userId);
        return findPermissions(userId).stream()
                .map(PermissionView::from)
                .toList();
    }

    @Override
    public Set<String> findPermissionCodes(Long userId) {
        /*
         * 保留原有“用户必须存在”的行为。
         *
         * 这样即使 Redis 中意外残留了已删除用户的缓存，
         * 也不会直接使用缓存授权。
         */
        requireUser(userId);

        Optional<Set<String>> cachedPermissions =
                permissionCacheService.get(userId);

        if (cachedPermissions.isPresent()) {
            /*
             * 返回一个新集合，
             * 防止调用方修改缓存服务返回的集合。
             */
            return new LinkedHashSet<>(
                    cachedPermissions.get()
            );
        }

        Set<String> permissionCodes =
                findPermissions(userId).stream()
                        .map(SysPermission::getPermissionCode)
                        .collect(
                                Collectors.toCollection(
                                        LinkedHashSet::new
                                )
                        );

        permissionCacheService.put(
                userId,
                permissionCodes
        );

        return permissionCodes;
    }


    private List<SysPermission> findPermissions(Long userId) {
        return permissionMapper.selectEffectivePermissionsByUserId(userId);
    }

    private SysUser requireUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new NotFoundException("用户不存在");
        }
        return user;
    }
}
