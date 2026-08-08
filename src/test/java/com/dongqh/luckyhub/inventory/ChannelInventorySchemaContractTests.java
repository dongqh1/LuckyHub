package com.dongqh.luckyhub.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ChannelInventorySchemaContractTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsTotalChannelReservationAndLedgerTables() {
        assertThat(tableExists("sku_inventory")).isTrue();
        assertThat(tableExists("inventory_channel_stock")).isTrue();
        assertThat(tableExists("inventory_reservation")).isTrue();
        assertThat(tableExists("inventory_ledger")).isTrue();
    }

    @Test
    void createsIdempotencyAndBusinessUniquenessIndexes() {
        assertThat(uniqueIndexes()).contains(
                "sku_inventory:sku_id",
                "inventory_channel_stock:sku_id,channel_code",
                "inventory_reservation:reservation_no",
                "inventory_ledger:business_no"
        );
    }

    @Test
    void recordsSuccessfulVersionNineMigration() {
        Integer successfulMigrations = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM flyway_schema_history
                WHERE version = '9' AND success = 1
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
}
