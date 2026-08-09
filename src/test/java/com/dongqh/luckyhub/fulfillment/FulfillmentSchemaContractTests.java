package com.dongqh.luckyhub.fulfillment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FulfillmentSchemaContractTests {

    @Autowired JdbcTemplate jdbc;

    @Test
    void createsFulfillmentAndSimulatorTables() {
        List<String> expected = List.of(
                "fulfillment_task", "fulfillment_attempt", "fulfillment_quarantine",
                "sim_coupon_record", "sim_points_record", "sim_membership_record",
                "sim_logistics_record", "sim_failure_rule");
        List<String> actual = jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name IN (
                  'fulfillment_task','fulfillment_attempt','fulfillment_quarantine',
                  'sim_coupon_record','sim_points_record','sim_membership_record',
                  'sim_logistics_record','sim_failure_rule')
                """, String.class);
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void seedsStageFourPermissionsForAdministrators() {
        List<String> permissions = jdbc.queryForList("""
                SELECT p.permission_code FROM sys_permission p
                JOIN sys_role_permission rp ON rp.permission_id = p.id
                JOIN sys_role r ON r.id = rp.role_id
                WHERE r.role_code = 'ADMIN' AND p.permission_code IN (
                  'fulfillment:create','fulfillment:read','fulfillment:operate','simulator:control')
                """, String.class);
        assertThat(permissions).containsExactlyInAnyOrder(
                "fulfillment:create", "fulfillment:read", "fulfillment:operate", "simulator:control");
    }

    @Test
    void enforcesStableBusinessIdentities() {
        List<String> indexes = jdbc.queryForList("""
                SELECT DISTINCT index_name FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND index_name IN (
                  'uk_fulfillment_task_no','uk_fulfillment_attempt_sequence','uk_fulfillment_quarantine_task',
                  'uk_sim_coupon_fulfillment','uk_sim_points_fulfillment',
                  'uk_sim_membership_fulfillment','uk_sim_logistics_fulfillment',
                  'uk_sim_failure_type')
                """, String.class);
        assertThat(indexes).hasSize(8);
    }
}
