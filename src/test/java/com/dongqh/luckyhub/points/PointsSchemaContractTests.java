package com.dongqh.luckyhub.points;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PointsSchemaContractTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsPointsAccountLedgerAndRedemptionTables() {
        assertThat(tableExists("points_account")).isTrue();
        assertThat(tableExists("points_ledger")).isTrue();
        assertThat(tableExists("points_redemption_order")).isTrue();
    }

    @Test
    void createsPointsIdempotencyAndReversalUniqueIndexes() {
        assertThat(uniqueIndexes()).contains(
                "points_account:user_id",
                "points_ledger:business_type,business_id",
                "points_ledger:reversal_of_ledger_id",
                "points_redemption_order:redemption_no",
                "points_redemption_order:reversal_no"
        );
    }

    @Test
    void grantsPointsPermissionsToUserAndAdminRoles() {
        assertThat(rolePermissions("USER")).contains("points:read", "points:redeem");
        assertThat(rolePermissions("ADMIN"))
                .contains("points:read", "points:redeem", "points:adjust");
    }

    @Test
    void extendsInventoryChecksForConfirmedReservationReversal() {
        assertThat(checkClause("inventory_reservation", "chk_inventory_reservation_status"))
                .contains("REVERSED");
        assertThat(checkClause("inventory_ledger", "chk_inventory_ledger_operation"))
                .contains("RETURN");
    }

    @Test
    void recordsSuccessfulVersionTenMigration() {
        Integer successfulMigrations = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM flyway_schema_history
                WHERE version = '10' AND success = 1
                """, Integer.class);

        assertThat(successfulMigrations).isEqualTo(1);
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = ?
                """, Integer.class, tableName);
        return count != null && count == 1;
    }

    private List<String> uniqueIndexes() {
        return jdbcTemplate.queryForList("""
                SELECT CONCAT(table_name, ':', GROUP_CONCAT(column_name ORDER BY seq_in_index))
                FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND non_unique = 0
                GROUP BY table_name, index_name
                """, String.class);
    }

    private List<String> rolePermissions(String roleCode) {
        return jdbcTemplate.queryForList("""
                SELECT permission.permission_code
                FROM sys_role_permission relation
                JOIN sys_role role_record ON role_record.id = relation.role_id
                JOIN sys_permission permission ON permission.id = relation.permission_id
                WHERE role_record.role_code = ?
                """, String.class, roleCode);
    }

    private String checkClause(String tableName, String constraintName) {
        return jdbcTemplate.queryForObject("""
                SELECT checks.check_clause
                FROM information_schema.table_constraints constraints_record
                JOIN information_schema.check_constraints checks
                  ON checks.constraint_schema = constraints_record.constraint_schema
                 AND checks.constraint_name = constraints_record.constraint_name
                WHERE constraints_record.table_schema = DATABASE()
                  AND constraints_record.table_name = ?
                  AND constraints_record.constraint_name = ?
                  AND constraints_record.constraint_type = 'CHECK'
                """, String.class, tableName, constraintName);
    }
}
