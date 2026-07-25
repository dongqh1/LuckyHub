package com.dongqh.luckyhub.rbac.service.Impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dongqh.luckyhub.auth.security.PasswordService;
import com.dongqh.luckyhub.auth.vo.CurrentUserView;
import com.dongqh.luckyhub.common.enums.CommonErrorCode;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.common.exception.NotFoundException;
import com.dongqh.luckyhub.rbac.dto.CreateUserCommand;
import com.dongqh.luckyhub.rbac.entity.SysRole;
import com.dongqh.luckyhub.rbac.entity.SysUser;
import com.dongqh.luckyhub.rbac.entity.SysUserRole;
import com.dongqh.luckyhub.rbac.mapper.SysRoleMapper;
import com.dongqh.luckyhub.rbac.mapper.SysUserMapper;
import com.dongqh.luckyhub.rbac.mapper.SysUserRoleMapper;
import com.dongqh.luckyhub.rbac.service.UserPermissionService;
import com.dongqh.luckyhub.rbac.service.UserService;
import com.dongqh.luckyhub.rbac.vo.UserView;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Service
@Validated
public class UserServiceImpl implements UserService {

    @Autowired
    private SysUserMapper userMapper;
    @Autowired
    private PasswordService passwordService;

    @Autowired
    private SysUserRoleMapper userRoleMapper;

    @Autowired
    private SysRoleMapper roleMapper;

    @Autowired
    private UserPermissionService userPermissionService;


    @Override
    @Transactional
    public UserView createUser(
            @Valid CreateUserCommand command
    ) {
        ensureUsernameAvailable(command.getUsername());

        SysUser user = new SysUser();
        user.setUsername(command.getUsername());
        user.setPassword(
                passwordService.hash(command.getPassword())
        );
        user.setNickname(command.getNickname());
        user.setStatus(1);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw usernameConflict();
        }

        return UserView.from(user);
    }

    //查询用户的详细信息通过id
    @Override
    @Transactional
    public CurrentUserView getAllById(Long id) {
        //1.查询用户基本信息
        SysUser user = userMapper.selectById(id);
        //判断
        if(user == null){
            throw new NotFoundException("用户不存在");
        }
        //2.通过用户这些基本信息查询用户的权限角色等
        List<Long> roleIds = userRoleMapper.selectList(
                Wrappers.<SysUserRole>lambdaQuery()
                        .eq(SysUserRole::getUserId,id)
        ).stream().map(SysUserRole::getRoleId).toList();

        //3.通过一系列id查找出来一系列的code
        // 3. 查询启用状态的角色编码
        List<String> roleCodes;

        if (roleIds.isEmpty()) {
            roleCodes = List.of();
        } else {
            roleCodes = roleMapper.selectList(
                            Wrappers.<SysRole>lambdaQuery()
                                    .in(SysRole::getId, roleIds)
                                    .eq(SysRole::getStatus, 1)
                                    .orderByAsc(
                                            SysRole::getRoleCode
                                    )
                    ).stream()
                    .map(SysRole::getRoleCode)
                    .toList();
        }
        //4. 查询权限,通过角色
        List<String> permissionsCodes =
                userPermissionService.findPermissionCodes(
                        id
                ).stream().toList();
        return new CurrentUserView(
                id,
                user.getUsername(),
                user.getNickname(),
                user.getStatus(),
                roleCodes,
                permissionsCodes
        );

    }

    @Override
    public List<UserView> listUser() {
        List<SysUser> userViews =  userMapper.selectList(
                Wrappers.<SysUser>lambdaQuery()
                        .orderByDesc(SysUser::getId)
        );

        return userViews.stream().map(UserView::from).toList();
    }

    private void ensureUsernameAvailable(String username) {
        boolean exists = userMapper.exists(
                Wrappers.<SysUser>lambdaQuery()
                        .eq(SysUser::getUsername, username)
        );

        if (exists) {
            throw usernameConflict();
        }
    }

    private BusinessException usernameConflict() {
        return new BusinessException(
                CommonErrorCode.DATA_CONFLICT,
                "用户名已存在"
        );
    }
}
