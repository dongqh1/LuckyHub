package com.dongqh.luckyhub.rbac.service;

import com.dongqh.luckyhub.common.enums.CommonErrorCode;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.rbac.dto.CreateUserCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class UserDefaultRoleRollbackTests {

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void disabledUserRoleRollsBackNewUser() {
        String username = "u_" + UUID.randomUUID()
                .toString().replace("-", "").substring(0, 12);
        jdbcTemplate.update(
                "UPDATE sys_role SET status = 0 WHERE role_code = 'USER'"
        );

        try {
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
            assertThat(userCount).isZero();
        } finally {
            jdbcTemplate.update(
                    "DELETE FROM sys_user WHERE username = ?",
                    username
            );
            jdbcTemplate.update(
                    "UPDATE sys_role SET status = 1 WHERE role_code = 'USER'"
            );
        }
    }
}
