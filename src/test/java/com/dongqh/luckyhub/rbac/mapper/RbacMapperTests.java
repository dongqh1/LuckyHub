package com.dongqh.luckyhub.rbac.mapper;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dongqh.luckyhub.rbac.entity.SysPermission;
import com.dongqh.luckyhub.rbac.entity.SysRole;
import com.dongqh.luckyhub.rbac.entity.SysUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class RbacMapperTests {

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysRoleMapper roleMapper;

    @Autowired
    private SysPermissionMapper permissionMapper;

    @Test
    void performsUserCrudWithAutoIncrementAndAuditFields() {
        String username = uniqueValue("user");
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword("encoded-password");
        user.setNickname("初始昵称");
        user.setStatus(1);

        assertThat(userMapper.insert(user)).isEqualTo(1);
        assertThat(user.getId()).isNotNull();
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
        assertThat(user.toString()).doesNotContain("encoded-password");

        SysUser saved = userMapper.selectById(user.getId());
        assertThat(saved.getUsername()).isEqualTo(username);
        assertThat(saved.getNickname()).isEqualTo("初始昵称");
        var originalUpdatedAt = saved.getUpdatedAt();

        saved.setNickname("修改后的昵称");
        saved.setStatus(0);
        assertThat(userMapper.updateById(saved)).isEqualTo(1);

        SysUser updated = userMapper.selectById(user.getId());
        assertThat(updated.getNickname()).isEqualTo("修改后的昵称");
        assertThat(updated.getStatus()).isZero();
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(originalUpdatedAt);

        assertThat(userMapper.deleteById(user.getId())).isEqualTo(1);
        assertThat(userMapper.selectById(user.getId())).isNull();
    }

    @Test
    void queriesRoleAndPermissionByUniqueCode() {
        String roleCode = uniqueValue("ROLE");
        SysRole role = new SysRole();
        role.setRoleCode(roleCode);
        role.setRoleName("测试角色");
        role.setStatus(1);
        roleMapper.insert(role);

        String permissionCode = uniqueValue("permission");
        SysPermission permission = new SysPermission();
        permission.setPermissionCode(permissionCode);
        permission.setPermissionName("测试权限");
        permissionMapper.insert(permission);

        SysRole savedRole = roleMapper.selectOne(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getRoleCode, roleCode));
        SysPermission savedPermission = permissionMapper.selectOne(Wrappers.<SysPermission>lambdaQuery()
                .eq(SysPermission::getPermissionCode, permissionCode));

        assertThat(savedRole.getId()).isNotNull();
        assertThat(savedRole.getCreatedAt()).isNotNull();
        assertThat(savedPermission.getId()).isNotNull();
        assertThat(savedPermission.getCreatedAt()).isNotNull();
    }

    @Test
    void enforcesUniqueUsername() {
        String username = uniqueValue("duplicate");
        userMapper.insert(newUser(username));

        assertThatThrownBy(() -> userMapper.insert(newUser(username)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void enforcesUniqueRoleAndPermissionCodes() {
        String roleCode = uniqueValue("ROLE_DUPLICATE");
        SysRole firstRole = new SysRole();
        firstRole.setRoleCode(roleCode);
        firstRole.setRoleName("角色一");
        firstRole.setStatus(1);
        roleMapper.insert(firstRole);

        SysRole duplicateRole = new SysRole();
        duplicateRole.setRoleCode(roleCode);
        duplicateRole.setRoleName("角色二");
        duplicateRole.setStatus(1);
        assertThatThrownBy(() -> roleMapper.insert(duplicateRole))
                .isInstanceOf(DataIntegrityViolationException.class);

        String permissionCode = uniqueValue("permission_duplicate");
        SysPermission firstPermission = new SysPermission();
        firstPermission.setPermissionCode(permissionCode);
        firstPermission.setPermissionName("权限一");
        permissionMapper.insert(firstPermission);

        SysPermission duplicatePermission = new SysPermission();
        duplicatePermission.setPermissionCode(permissionCode);
        duplicatePermission.setPermissionName("权限二");
        assertThatThrownBy(() -> permissionMapper.insert(duplicatePermission))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private SysUser newUser(String username) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword("encoded-password");
        user.setStatus(1);
        return user;
    }

    private String uniqueValue(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
