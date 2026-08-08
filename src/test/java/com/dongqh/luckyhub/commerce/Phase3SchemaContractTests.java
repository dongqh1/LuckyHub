package com.dongqh.luckyhub.commerce;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class Phase3SchemaContractTests {

    @Autowired JdbcTemplate jdbc;

    @Test
    void createsCouponMembershipOrderAndPaymentTables() {
        List<String> expected = List.of(
                "coupon_template", "user_coupon", "coupon_issue_record",
                "membership_product", "user_membership", "membership_grant_record",
                "mall_order", "payment_order");
        List<String> actual = jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name IN (
                  'coupon_template','user_coupon','coupon_issue_record',
                  'membership_product','user_membership','membership_grant_record',
                  'mall_order','payment_order')
                """, String.class);
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void seedsPhaseThreePermissions() {
        List<String> permissions = jdbc.queryForList("""
                SELECT permission_code FROM sys_permission
                WHERE permission_code IN ('coupon:read','coupon:manage','membership:read',
                  'membership:manage','order:create','order:read','order:cancel','payment:create','payment:simulate')
                """, String.class);
        assertThat(permissions).hasSize(9);
    }

    @Test
    void enforcesUniqueAssetAndBusinessIdentities() {
        List<String> indexes = jdbc.queryForList("""
                SELECT DISTINCT index_name FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND index_name IN (
                  'uk_coupon_template_code','uk_user_coupon_no','uk_coupon_issue_business',
                  'uk_membership_product_code','uk_user_membership_user','uk_membership_grant_business',
                  'uk_mall_order_no','uk_payment_no')
                """, String.class);
        assertThat(indexes).hasSize(8);
    }
}
