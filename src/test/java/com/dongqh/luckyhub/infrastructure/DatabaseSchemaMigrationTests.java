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
            "user_benefit"
    );

    private static final Set<String> REQUIRED_UNIQUE_INDEXES = Set.of(
            "sys_user:username",
            "sys_role:role_code",
            "sys_permission:permission_code",
            "sys_user_role:user_id,role_id",
            "sys_role_permission:role_id,permission_id",
            "marketing_activity_prize:activity_id,prize_id",
            "lottery_draw_order:request_id",
            "lottery_draw_record:request_id,sequence_no"
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
