package com.dongqh.luckyhub.rbac.service;

import com.dongqh.luckyhub.common.enums.CommonErrorCode;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.rbac.dto.CreateUserCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ResourceLock("sys_role.USER")
class UserDefaultRoleRollbackTests {

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void disabledUserRoleRollsBackNewUser() {
        RoleSnapshot original = loadUserRole();
        String username = uniqueUsername();

        try {
            assertThat(jdbcTemplate.update(
                    "UPDATE sys_role SET status = 0 WHERE id = ?",
                    original.id()
            )).isEqualTo(1);

            assertConfigurationFailureAndRollback(username);
        } finally {
            cleanupAndRestore(username, original);
        }
    }

    @Test
    void missingUserRoleRollsBackNewUser() {
        RoleSnapshot original = loadUserRole();
        String username = uniqueUsername();
        String placeholder = "USER_MISSING_" + UUID.randomUUID()
                .toString().replace("-", "").substring(0, 12);

        try {
            assertThat(jdbcTemplate.update(
                    "UPDATE sys_role SET role_code = ? WHERE id = ?",
                    placeholder,
                    original.id()
            )).isEqualTo(1);

            assertConfigurationFailureAndRollback(username);
        } finally {
            cleanupAndRestore(username, original);
        }
    }

    private void assertConfigurationFailureAndRollback(String username) {
        Integer relationCountBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user_role",
                Integer.class
        );

        assertThatThrownBy(() -> userService.createUser(
                new CreateUserCommand(username, "Password1!", "回滚测试")
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(CommonErrorCode.SYSTEM_ERROR)
        );

        Integer userCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE username = ?",
                Integer.class,
                username
        );
        Integer relationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user_role",
                Integer.class
        );
        assertThat(userCount).isZero();
        assertThat(relationCount).isEqualTo(relationCountBefore);
    }

    private RoleSnapshot loadUserRole() {
        return jdbcTemplate.queryForObject(
                """
                SELECT id, role_code, status
                FROM sys_role
                WHERE role_code = 'USER'
                """,
                (resultSet, rowNumber) -> new RoleSnapshot(
                        resultSet.getLong("id"),
                        resultSet.getString("role_code"),
                        resultSet.getInt("status")
                )
        );
    }

    private void cleanupRelation(String username) {
        jdbcTemplate.update(
                """
                DELETE relation FROM sys_user_role relation
                JOIN sys_user user_record ON user_record.id = relation.user_id
                WHERE user_record.username = ?
                """,
                username
        );
    }

    private void cleanupUser(String username) {
        jdbcTemplate.update(
                "DELETE FROM sys_user WHERE username = ?",
                username
        );
    }

    private void cleanupAndRestore(
            String username,
            RoleSnapshot original
    ) {
        try {
            try {
                cleanupRelation(username);
            } finally {
                cleanupUser(username);
            }
        } finally {
            restoreRole(original);
        }
    }

    private void restoreRole(RoleSnapshot original) {
        assertThat(jdbcTemplate.update(
                "UPDATE sys_role SET role_code = ?, status = ? WHERE id = ?",
                original.roleCode(),
                original.status(),
                original.id()
        )).isEqualTo(1);
    }

    private String uniqueUsername() {
        return "u_" + UUID.randomUUID()
                .toString().replace("-", "").substring(0, 12);
    }

    private record RoleSnapshot(long id, String roleCode, int status) {
    }
}
