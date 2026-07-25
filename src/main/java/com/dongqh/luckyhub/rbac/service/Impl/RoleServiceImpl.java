package com.dongqh.luckyhub.rbac.service.Impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dongqh.luckyhub.common.enums.CommonErrorCode;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.common.exception.NotFoundException;
import com.dongqh.luckyhub.rbac.dto.CreateRoleCommand;
import com.dongqh.luckyhub.rbac.entity.SysRole;
import com.dongqh.luckyhub.rbac.mapper.SysRoleMapper;
import com.dongqh.luckyhub.rbac.service.RoleService;
import com.dongqh.luckyhub.rbac.vo.RoleView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RoleServiceImpl implements RoleService {

    @Autowired
    private SysRoleMapper roleMapper;

    @Override
    public RoleView createRole(CreateRoleCommand command) {
        //判断code合法

        ensureRoleCodeAvailable(command.roleCode());

        SysRole sysRole = new SysRole();
        sysRole.setRoleCode(command.roleCode());
        sysRole.setRoleName(command.roleName());
        sysRole.setStatus(1);

        roleMapper.insert(sysRole);


        return RoleView.from(sysRole);
    }

    @Override
    public List<RoleView> listRole() {

        List<SysRole> roleList = roleMapper.selectList(
                Wrappers.<SysRole>lambdaQuery()
                        .orderByDesc(SysRole::getId)
        );

        return roleList.stream()
                .map(RoleView::from)
                .toList();
    }

    @Override
    public RoleView getById(Long roleId) {

        SysRole sysRole = roleMapper.selectById(roleId);
        if(sysRole == null)
            throw new NotFoundException("角色不存在");

        return RoleView.from(sysRole);
    }

    private void ensureRoleCodeAvailable(String roleCode) {
        boolean exists = roleMapper.exists(
                Wrappers.<SysRole>lambdaQuery()
                        .eq(SysRole::getRoleCode, roleCode)
        );

        if (exists) {
            throw roleCodeConflict();
        }
    }
    private BusinessException roleCodeConflict() {
        return new BusinessException(
                CommonErrorCode.DATA_CONFLICT,
                "角色编码已存在"
        );
    }
}
