package com.dongqh.luckyhub.lottery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LotterySchemaContractTests {

    private static final Set<String> ORDINARY_PERMISSIONS = Set.of(
            "lottery:activity:read",
            "lottery:draw",
            "lottery:draw:read",
            "lottery:record:read",
            "benefit:read"
    );

    private static final Set<String> ALL_PERMISSIONS = Set.of(
            "lottery:order:read:all",
            "lottery:draw:read:all",
            "lottery:record:read:all",
            "benefit:read:all"
    );

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    LotterySchemaContractTests(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void addsLotteryCoreColumnsAndTables() {
        assertThat(column("marketing_activity", "no_win_weight")).isPresent();
        assertThat(column("lottery_draw_order", "draw_date")).isPresent();
        assertThat(column("lottery_draw_record", "result_type")).isPresent();
        assertThat(column("lottery_draw_record", "prize_name")).isPresent();
        assertThat(column("user_benefit", "draw_record_id")).isPresent();
        assertThat(table("message_outbox")).isPresent();
        assertThat(table("message_consume_record")).isPresent();
    }

    @Test
    void requiresDrawSourceAndPrizeTypeForBenefits() {
        assertThat(column("user_benefit", "draw_record_id"))
                .get()
                .extracting(metadata -> metadata.get("IS_NULLABLE"))
                .isEqualTo("NO");
        assertThat(column("user_benefit", "prize_type"))
                .get()
                .extracting(metadata -> metadata.get("IS_NULLABLE"))
                .isEqualTo("NO");
    }

    @Test
    void seedsLotteryRolesAndPermissions() {
        List<String> roles = jdbcTemplate.queryForList("""
                SELECT role_code
                FROM sys_role
                WHERE role_code = 'USER'
                """, String.class);
        List<String> permissions = jdbcTemplate.queryForList("""
                SELECT permission_code
                FROM sys_permission
                WHERE permission_code IN (
                    'lottery:activity:read',
                    'lottery:draw',
                    'lottery:draw:read',
                    'lottery:record:read',
                    'benefit:read',
                    'lottery:order:read:all',
                    'lottery:draw:read:all',
                    'lottery:record:read:all',
                    'benefit:read:all'
                )
                """, String.class);

        assertThat(roles).containsExactly("USER");
        assertThat(permissions).containsExactlyInAnyOrderElementsOf(allLotteryPermissions());
    }

    @Test
    void grantsLotteryPermissionsToUserAndAdminRoles() {
        assertThat(rolePermissions("USER"))
                .containsExactlyInAnyOrderElementsOf(ORDINARY_PERMISSIONS);
        assertThat(rolePermissions("ADMIN"))
                .containsAll(allLotteryPermissions());
    }

    @Test
    void associatesEveryExistingUserWithUserRole() {
        Integer usersWithoutUserRole = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM sys_user user_record
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM sys_user_role user_role
                    JOIN sys_role role_record ON role_record.id = user_role.role_id
                    WHERE user_role.user_id = user_record.id
                      AND role_record.role_code = 'USER'
                )
                """, Integer.class);

        assertThat(usersWithoutUserRole).isZero();
    }

    private Optional<Map<String, Object>> column(String tableName, String columnName) {
        return jdbcTemplate.queryForList("""
                SELECT column_name, data_type, is_nullable
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """, tableName, columnName).stream().findFirst();
    }

    private Optional<String> table(String tableName) {
        return jdbcTemplate.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_type = 'BASE TABLE'
                  AND table_name = ?
                """, String.class, tableName).stream().findFirst();
    }

    private Set<String> rolePermissions(String roleCode) {
        return Set.copyOf(jdbcTemplate.queryForList("""
                SELECT permission.permission_code
                FROM sys_role_permission role_permission
                JOIN sys_role role_record ON role_record.id = role_permission.role_id
                JOIN sys_permission permission ON permission.id = role_permission.permission_id
                WHERE role_record.role_code = ?
                  AND permission.permission_code IN (
                    'lottery:activity:read',
                    'lottery:draw',
                    'lottery:draw:read',
                    'lottery:record:read',
                    'benefit:read',
                    'lottery:order:read:all',
                    'lottery:draw:read:all',
                    'lottery:record:read:all',
                    'benefit:read:all'
                  )
                """, String.class, roleCode));
    }

    private Set<String> allLotteryPermissions() {
        Set<String> permissions = new java.util.HashSet<>(ORDINARY_PERMISSIONS);
        permissions.addAll(ALL_PERMISSIONS);
        return permissions;
    }
}
