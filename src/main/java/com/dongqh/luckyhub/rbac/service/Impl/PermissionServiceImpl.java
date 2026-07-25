package com.dongqh.luckyhub.rbac.service.Impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dongqh.luckyhub.common.enums.CommonErrorCode;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.common.exception.NotFoundException;
import com.dongqh.luckyhub.rbac.dto.CreatePermissionCommand;
import com.dongqh.luckyhub.rbac.entity.SysPermission;
import com.dongqh.luckyhub.rbac.mapper.SysPermissionMapper;
import com.dongqh.luckyhub.rbac.service.PermissionService;
import com.dongqh.luckyhub.rbac.vo.PermissionView;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PermissionServiceImpl implements PermissionService {

    private final SysPermissionMapper permissionMapper;

    public PermissionServiceImpl(SysPermissionMapper permissionMapper) {
        this.permissionMapper = permissionMapper;
    }

    @Override
    @Transactional
    public PermissionView createPermission(CreatePermissionCommand command) {
        ensurePermissionCodeAvailable(command.permissionCode());

        SysPermission permission = new SysPermission();
        permission.setPermissionCode(command.permissionCode());
        permission.setPermissionName(command.permissionName().trim());

        try {
            permissionMapper.insert(permission);
        } catch (DuplicateKeyException exception) {
            throw permissionCodeConflict();
        }

        return PermissionView.from(permission);
    }

    @Override
    public List<PermissionView> listPermissions() {
        return permissionMapper.selectList(
                        Wrappers.<SysPermission>lambdaQuery()
                                .orderByAsc(SysPermission::getPermissionCode)
                ).stream()
                .map(PermissionView::from)
                .toList();
    }

    @Override
    public PermissionView getPermission(Long permissionId) {
        SysPermission permission = permissionMapper.selectById(permissionId);
        if (permission == null) {
            throw new NotFoundException("权限不存在");
        }
        return PermissionView.from(permission);
    }

    private void ensurePermissionCodeAvailable(String permissionCode) {
        boolean exists = permissionMapper.exists(
                Wrappers.<SysPermission>lambdaQuery()
                        .eq(SysPermission::getPermissionCode, permissionCode)
        );
        if (exists) {
            throw permissionCodeConflict();
        }
    }

    private BusinessException permissionCodeConflict() {
        return new BusinessException(
                CommonErrorCode.DATA_CONFLICT,
                "权限编码已存在"
        );
    }
}
