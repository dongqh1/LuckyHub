package com.dongqh.luckyhub.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DatabaseSchemaMigrationTests {

    private static final Set<String> PRIZE_PERMISSIONS = Set.of(
            "prize:create",
            "prize:read",
            "prize:update",
            "prize:disable",
            "prize:image:upload"
    );

    private static final Set<String> ACTIVITY_PERMISSIONS = Set.of(
            "activity:create",
            "activity:read",
            "activity:update",
            "activity:publish",
            "activity:disable",
            "activity:restore",
            "activity:prize:manage"
    );

    private static final Set<String> BUSINESS_TABLES = Set.of(
            "sys_user",
            "sys_role",
            "sys_permission",
            "sys_user_role",
            "sys_role_permission",
            "marketing_prize",
            "marketing_activity",
            "marketing_activity_prize",
            "lottery_draw_order",
            "lottery_draw_record",
            "user_benefit",
            "message_outbox",
            "message_consume_record",
            "product",
            "product_sku",
            "reward_definition",
            "sku_inventory",
            "inventory_channel_stock",
            "inventory_reservation",
            "inventory_ledger",
            "points_account",
            "points_ledger",
            "points_redemption_order"
    );

    private static final Set<String> REQUIRED_UNIQUE_INDEXES = Set.of(
            "sys_user:username",
            "sys_role:role_code",
            "sys_permission:permission_code",
            "sys_user_role:user_id,role_id",
            "sys_role_permission:role_id,permission_id",
            "marketing_activity_prize:activity_id,prize_id",
            "lottery_draw_order:request_id",
            "lottery_draw_record:request_id,sequence_no",
            "user_benefit:draw_record_id",
            "message_outbox:event_id",
            "message_consume_record:event_id,consumer_name",
            "sku_inventory:sku_id",
            "inventory_channel_stock:sku_id,channel_code",
            "inventory_reservation:reservation_no",
            "inventory_ledger:business_no",
            "points_account:user_id",
            "points_ledger:business_type,business_id",
            "points_ledger:reversal_of_ledger_id",
            "points_redemption_order:redemption_no",
            "points_redemption_order:reversal_no"
    );

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    DatabaseSchemaMigrationTests(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void createsAllBusinessTables() {
        List<String> tables = jdbcTemplate.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                """, String.class);

        assertThat(tables).containsExactlyInAnyOrderElementsOf(BUSINESS_TABLES);
    }

    @Test
    void recordsSuccessfulVersionOneMigration() {
        Integer successfulMigrations = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version = '1'
                  AND success = 1
                """, Integer.class);

        assertThat(successfulMigrations).isEqualTo(1);
    }

    @Test
    void recordsSuccessfulVersionFiveMigration() {
        Integer successfulMigrations = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version = '5'
                  AND success = 1
                """, Integer.class);

        assertThat(successfulMigrations).isEqualTo(1);
    }

    @Test
    void addsOutboxDeliveryErrorThroughVersionSixMigration() {
        Integer successfulMigrations = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version = '6'
                  AND success = 1
                """, Integer.class);
        Integer lastErrorColumn = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'message_outbox'
                  AND column_name = 'last_error'
                """, Integer.class);

        assertThat(successfulMigrations).isEqualTo(1);
        assertThat(lastErrorColumn).isEqualTo(1);
    }

    @Test
    void addsRecoverableOutboxLeaseThroughVersionSevenMigration() {
        Integer successfulMigrations = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM flyway_schema_history
                WHERE version = '7' AND success = 1
                """, Integer.class);
        Integer claimTokenColumn = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'message_outbox'
                  AND column_name = 'claim_token'
                """, Integer.class);

        assertThat(successfulMigrations).isEqualTo(1);
        assertThat(claimTokenColumn).isEqualTo(1);
    }

    @Test
    void addsPointsAccountAndRedemptionThroughVersionTenMigration() {
        Integer successfulMigrations = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM flyway_schema_history
                WHERE version = '10' AND success = 1
                """, Integer.class);

        assertThat(successfulMigrations).isEqualTo(1);
    }

    @Test
    void seedsPrizePermissionsAndGrantsThemToAdmin() {
        Integer successfulMigration = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version = '3'
                  AND success = 1
                """, Integer.class);
        List<String> permissionCodes = jdbcTemplate.queryForList("""
                SELECT permission_code
                FROM sys_permission
                WHERE permission_code LIKE 'prize:%'
                """, String.class);
        List<String> adminPermissionCodes = jdbcTemplate.queryForList("""
                SELECT permission.permission_code
                FROM sys_role_permission role_permission
                JOIN sys_role role_record ON role_record.id = role_permission.role_id
                JOIN sys_permission permission ON permission.id = role_permission.permission_id
                WHERE role_record.role_code = 'ADMIN'
                  AND permission.permission_code LIKE 'prize:%'
                """, String.class);

        assertThat(successfulMigration).isEqualTo(1);
        assertThat(permissionCodes).containsExactlyInAnyOrderElementsOf(PRIZE_PERMISSIONS);
        assertThat(adminPermissionCodes).containsExactlyInAnyOrderElementsOf(PRIZE_PERMISSIONS);
    }

    @Test
    void seedsActivityPermissionsAndGrantsThemToAdmin() {
        Integer successfulMigration = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version = '4'
                  AND success = 1
                """, Integer.class);
        List<String> permissionCodes = jdbcTemplate.queryForList("""
                SELECT permission_code
                FROM sys_permission
                WHERE permission_code LIKE 'activity:%'
                """, String.class);
        List<String> adminPermissionCodes = jdbcTemplate.queryForList("""
                SELECT permission.permission_code
                FROM sys_role_permission role_permission
                JOIN sys_role role_record ON role_record.id = role_permission.role_id
                JOIN sys_permission permission ON permission.id = role_permission.permission_id
                WHERE role_record.role_code = 'ADMIN'
                  AND permission.permission_code LIKE 'activity:%'
                """, String.class);

        assertThat(successfulMigration).isEqualTo(1);
        assertThat(permissionCodes).containsExactlyInAnyOrderElementsOf(ACTIVITY_PERMISSIONS);
        assertThat(adminPermissionCodes).containsExactlyInAnyOrderElementsOf(ACTIVITY_PERMISSIONS);
    }

    @Test
    void createsRequiredUniqueIndexes() {
        List<String> uniqueIndexes = jdbcTemplate.queryForList("""
                SELECT CONCAT(table_name, ':', GROUP_CONCAT(column_name ORDER BY seq_in_index))
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND non_unique = 0
                GROUP BY table_name, index_name
                """, String.class);

        assertThat(uniqueIndexes).containsAll(REQUIRED_UNIQUE_INDEXES);
    }
}
