package com.dongqh.luckyhub.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CatalogRewardSchemaContractTests {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void createsCatalogRewardTablesAndCompatibilityLink() {
        assertThat(tableExists("product")).isTrue();
        assertThat(tableExists("product_sku")).isTrue();
        assertThat(tableExists("reward_definition")).isTrue();
        assertThat(columnExists("marketing_prize", "reward_definition_id")).isTrue();
    }

    @Test
    void seedsCatalogRewardAndInventoryPermissions() {
        List<String> permissions = jdbcTemplate.queryForList("""
                SELECT permission_code FROM sys_permission
                WHERE permission_code IN (
                    'catalog:read', 'catalog:manage', 'reward:manage', 'inventory:manage'
                )
                """, String.class);
        assertThat(permissions).containsExactlyInAnyOrder(
                "catalog:read", "catalog:manage", "reward:manage", "inventory:manage");
    }

    @Test
    void grantsReadToUserAndAllNewPermissionsToAdmin() {
        assertThat(rolePermissions("USER")).contains("catalog:read");
        assertThat(rolePermissions("ADMIN")).contains(
                "catalog:read", "catalog:manage", "reward:manage", "inventory:manage");
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = ?
                """, Integer.class, tableName);
        return count != null && count == 1;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
                """, Integer.class, tableName, columnName);
        return count != null && count == 1;
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
}
