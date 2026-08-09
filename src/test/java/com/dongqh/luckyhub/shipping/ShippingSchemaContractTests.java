package com.dongqh.luckyhub.shipping;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ShippingSchemaContractTests {

    @Autowired JdbcTemplate jdbc;

    @Test
    void migratesV17AndCreatesExactlyFiveShippingTables() {
        assertThat(jdbc.queryForObject("""
                SELECT version FROM flyway_schema_history
                WHERE success=1 ORDER BY installed_rank DESC LIMIT 1
                """, String.class))
                .isEqualTo("17");
        assertThat(jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema=DATABASE() AND table_name LIKE 'shipping_%'
                   OR table_schema=DATABASE() AND table_name='user_shipping_address'
                """, String.class)).containsExactlyInAnyOrder(
                "user_shipping_address", "shipping_address_snapshot", "shipping_order",
                "shipping_tracking_event", "shipping_callback_receipt");
    }

    @Test
    void keepsAddressPayloadEncryptedAndDisplayFieldsBounded() {
        List<String> addressTables = List.of("user_shipping_address", "shipping_address_snapshot");
        List<String> ciphertextColumns = List.of(
                "receiver_ciphertext", "phone_ciphertext", "province_ciphertext", "city_ciphertext",
                "district_ciphertext", "detail_ciphertext");
        for (String table : addressTables) {
            Map<String, String> types = columnTypes(table);
            for (String column : ciphertextColumns) {
                assertThat(types).containsEntry(column, "text");
            }
            assertThat(types.get("receiver_masked")).isEqualTo("varchar(64)");
            assertThat(types.get("phone_masked")).isEqualTo("varchar(32)");
            assertThat(types.get("region_masked")).isEqualTo("varchar(200)");
        }
        assertThat(columnNames("shipping_callback_receipt"))
                .contains("nonce_digest", "signature_digest")
                .doesNotContain("nonce", "signature", "payload", "provider_response");
    }

    @Test
    void enforcesExactEnumAndQuantityChecks() {
        Map<String, String> checks = jdbc.query("""
                SELECT constraint_name, check_clause FROM information_schema.check_constraints
                WHERE constraint_schema=DATABASE() AND constraint_name IN (
                  'chk_shipping_address_default','chk_shipping_address_status',
                  'chk_shipping_snapshot_source_type','chk_shipping_order_source_type',
                  'chk_shipping_order_status','chk_shipping_order_quantity',
                  'chk_shipping_tracking_event_type','chk_shipping_callback_event_type',
                  'chk_shipping_callback_status','chk_inventory_ledger_operation')
                """, resultSet -> {
            java.util.LinkedHashMap<String, String> result = new java.util.LinkedHashMap<>();
            while (resultSet.next()) {
                result.put(resultSet.getString(1), normalize(resultSet.getString(2)));
            }
            return result;
        });
        assertThat(checks).containsEntry("chk_shipping_address_default", "`is_default` in (0,1)");
        assertThat(checks).containsEntry("chk_shipping_address_status", "`status` in (_utf8mb4'active',_utf8mb4'deleted')");
        assertThat(checks).containsEntry("chk_shipping_snapshot_source_type", sourceTypeCheck());
        assertThat(checks).containsEntry("chk_shipping_order_source_type", sourceTypeCheck());
        assertThat(checks).containsEntry("chk_shipping_order_status",
                "`status` in (_utf8mb4'ready',_utf8mb4'fulfilling',_utf8mb4'shipped',_utf8mb4'in_transit',_utf8mb4'delivered',_utf8mb4'failed',_utf8mb4'terminated')");
        assertThat(checks).containsEntry("chk_shipping_order_quantity", "`quantity` > 0");
        assertThat(checks).containsEntry("chk_shipping_tracking_event_type", trackingEventCheck());
        assertThat(checks).containsEntry("chk_shipping_callback_event_type", trackingEventCheck());
        assertThat(checks).containsEntry("chk_shipping_callback_status",
                "`status` in (_utf8mb4'received',_utf8mb4'processed',_utf8mb4'rejected')");
        assertThat(checks.get("chk_inventory_ledger_operation")).contains("_utf8mb4'claim_return'");
    }

    @Test
    void createsStableUniqueBusinessIdentities() {
        assertThat(uniqueIndexes()).contains(
                "shipping_address_snapshot:snapshot_no",
                "shipping_address_snapshot:source_type,source_id",
                "shipping_order:source_type,source_id",
                "shipping_order:shipping_no",
                "shipping_order:fulfillment_no",
                "shipping_order:claim_request_id",
                "shipping_order:waybill_no",
                "shipping_tracking_event:provider_event_id",
                "shipping_callback_receipt:callback_id",
                "shipping_callback_receipt:nonce_digest");
    }

    @Test
    void addsNullableForeignIdentityColumnsWithoutChangingRewardSnapshots() {
        assertNullableColumns("user_benefit", "claim_deadline", "claimed_at", "shipping_order_id");
        assertNullableColumns("mall_order", "address_snapshot_id", "shipping_order_id");
        assertNullableColumns("points_redemption_order", "address_snapshot_id", "shipping_order_id");
        assertThat(columnNames("user_benefit")).contains(
                "reward_definition_id", "reward_type", "reward_target_id", "reward_quantity",
                "reward_payload", "reward_fingerprint", "fulfillment_no");
    }

    @Test
    void grantsShippingPermissionsToApprovedRoles() {
        List<String> grants = jdbc.queryForList("""
                SELECT CONCAT(r.role_code, ':', p.permission_code)
                FROM sys_role r
                JOIN sys_role_permission rp ON rp.role_id=r.id
                JOIN sys_permission p ON p.id=rp.permission_id
                WHERE p.permission_code IN ('shipping:address:manage','shipping:read','shipping:operate')
                """, String.class);
        assertThat(grants).containsExactlyInAnyOrder(
                "USER:shipping:address:manage", "USER:shipping:read",
                "ADMIN:shipping:address:manage", "ADMIN:shipping:read", "ADMIN:shipping:operate");
    }

    private Map<String, String> columnTypes(String table) {
        return jdbc.query("""
                SELECT column_name, column_type FROM information_schema.columns
                WHERE table_schema=DATABASE() AND table_name=?
                """, resultSet -> {
            java.util.LinkedHashMap<String, String> result = new java.util.LinkedHashMap<>();
            while (resultSet.next()) {
                result.put(resultSet.getString(1), resultSet.getString(2).toLowerCase(Locale.ROOT));
            }
            return result;
        }, table);
    }

    private List<String> columnNames(String table) {
        return jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema=DATABASE() AND table_name=?
                """, String.class, table);
    }

    private void assertNullableColumns(String table, String... columns) {
        List<String> actual = jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema=DATABASE() AND table_name=? AND is_nullable='YES'
                """, String.class, table);
        assertThat(actual).contains(columns);
    }

    private List<String> uniqueIndexes() {
        return jdbc.queryForList("""
                SELECT CONCAT(table_name, ':', GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ','))
                FROM information_schema.statistics
                WHERE table_schema=DATABASE() AND non_unique=0 AND index_name <> 'PRIMARY'
                  AND table_name IN ('user_shipping_address','shipping_address_snapshot','shipping_order',
                                     'shipping_tracking_event','shipping_callback_receipt')
                GROUP BY table_name,index_name
                """, String.class);
    }

    private String normalize(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim()
                .replace("( ", "(").replace(" )", ")").replace(", ", ",")
                .replace("\\'", "'");
        if (normalized.startsWith("(") && normalized.endsWith(")")) {
            return normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }

    private String sourceTypeCheck() {
        return "`source_type` in (_utf8mb4'lottery_benefit',_utf8mb4'cash_order',_utf8mb4'points_redemption')";
    }

    private String trackingEventCheck() {
        return "`event_type` in (_utf8mb4'picked_up',_utf8mb4'in_transit',_utf8mb4'out_for_delivery',_utf8mb4'delivered')";
    }
}
