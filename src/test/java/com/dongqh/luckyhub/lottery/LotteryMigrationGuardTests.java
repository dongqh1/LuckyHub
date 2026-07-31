package com.dongqh.luckyhub.lottery;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.flyway.enabled=false")
class LotteryMigrationGuardTests {

    private final DataSource dataSource;
    private final Environment environment;

    @Autowired
    LotteryMigrationGuardTests(DataSource dataSource, Environment environment) {
        this.dataSource = dataSource;
        this.environment = environment;
    }

    @Test
    void rejectsLegacyBenefitsBeforeAddingRequiredDrawSourceColumns() throws Exception {
        String schemaName = "luckyhub_v5_guard_" + UUID.randomUUID().toString().replace("-", "");
        String rootPassword = environment.getRequiredProperty("MYSQL_ROOT_PASSWORD");
        String applicationUrl;
        try (Connection connection = dataSource.getConnection()) {
            applicationUrl = connection.getMetaData().getURL();
        }

        try (Connection adminConnection = DriverManager.getConnection(applicationUrl, "root", rootPassword);
             Statement adminStatement = adminConnection.createStatement()) {
            adminStatement.execute("CREATE DATABASE `" + schemaName
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            try {
                String schemaUrl = replaceSchema(applicationUrl, schemaName);
                Flyway versionFourFlyway = Flyway.configure()
                        .dataSource(schemaUrl, "root", rootPassword)
                        .locations("classpath:db/migration")
                        .target(MigrationVersion.fromVersion("4"))
                        .load();
                versionFourFlyway.migrate();

                try (Connection schemaConnection = DriverManager.getConnection(schemaUrl, "root", rootPassword);
                     Statement schemaStatement = schemaConnection.createStatement()) {
                    schemaStatement.executeUpdate("""
                            INSERT INTO user_benefit (
                                user_id, prize_id, quantity, status, obtained_at
                            ) VALUES (1, 1, 1, 'AVAILABLE', CURRENT_TIMESTAMP(3))
                            """);
                }

                Flyway versionFiveFlyway = Flyway.configure()
                        .dataSource(schemaUrl, "root", rootPassword)
                        .locations("classpath:db/migration")
                        .load();

                assertThatThrownBy(versionFiveFlyway::migrate)
                        .hasStackTraceContaining("chk_v5_requires_empty_user_benefit");

                try (Connection schemaConnection = DriverManager.getConnection(schemaUrl, "root", rootPassword);
                     Statement schemaStatement = schemaConnection.createStatement()) {
                    try (var columns = schemaStatement.executeQuery("""
                            SELECT COUNT(*)
                            FROM information_schema.columns
                            WHERE table_schema = DATABASE()
                              AND table_name = 'user_benefit'
                              AND column_name IN ('draw_record_id', 'prize_type')
                            """)) {
                        columns.next();
                        assertThat(columns.getInt(1)).isZero();
                    }
                    schemaStatement.executeUpdate("DELETE FROM user_benefit");
                }
                versionFiveFlyway.repair();
                versionFiveFlyway.migrate();

                assertThat(versionFiveFlyway.info().current().getVersion().getVersion()).isEqualTo("6");
            } finally {
                adminStatement.execute("DROP DATABASE IF EXISTS `" + schemaName + "`");
            }
        }
    }

    private String replaceSchema(String jdbcUrl, String schemaName) {
        int schemaStart = jdbcUrl.indexOf('/', "jdbc:mysql://".length()) + 1;
        int queryStart = jdbcUrl.indexOf('?', schemaStart);
        String suffix = queryStart >= 0 ? jdbcUrl.substring(queryStart) : "";
        return jdbcUrl.substring(0, schemaStart) + schemaName + suffix;
    }
}
