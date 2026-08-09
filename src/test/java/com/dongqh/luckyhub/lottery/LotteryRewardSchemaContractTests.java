package com.dongqh.luckyhub.lottery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class LotteryRewardSchemaContractTests {

    @Autowired JdbcTemplate jdbc;

    @Test
    void migratesStageFiveTablesAndSnapshotColumnsThroughV16() {
        List<String> tables = jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema=DATABASE() AND table_name IN (
                  'draw_chance_account','draw_chance_ledger',
                  'draw_chance_reservation','lottery_reward_quarantine')
                """, String.class);
        assertThat(tables).containsExactlyInAnyOrder(
                "draw_chance_account", "draw_chance_ledger",
                "draw_chance_reservation", "lottery_reward_quarantine");

        for (String table : List.of("lottery_draw_record", "user_benefit")) {
            Map<String, Map<String, Object>> columns = jdbc.queryForList("""
                    SELECT column_name,data_type,is_nullable,character_maximum_length
                    FROM information_schema.columns
                    WHERE table_schema=DATABASE() AND table_name=?
                      AND column_name IN ('reward_definition_id','reward_type','reward_target_id',
                        'reward_quantity','reward_payload','reward_fingerprint')
                    """, table).stream().collect(java.util.stream.Collectors.toMap(
                    row -> row.get("column_name").toString(), row -> row));
            assertThat(columns).hasSize(6);
            assertThat(columns.get("reward_payload").get("data_type")).isEqualTo("json");
            assertThat(columns.get("reward_fingerprint").get("data_type")).isEqualTo("char");
            assertThat(((Number) columns.get("reward_fingerprint")
                    .get("character_maximum_length")).longValue()).isEqualTo(64L);
            assertThat(columns.values()).allSatisfy(row ->
                    assertThat(row.get("is_nullable")).isEqualTo("YES"));
        }

        Integer migrated = jdbc.queryForObject("""
                SELECT COUNT(*) FROM flyway_schema_history
                WHERE version='16' AND success=1
                """, Integer.class);
        assertThat(migrated).isOne();
    }

    @Test
    void enforcesStableStageFiveBusinessIdentities() {
        List<String> indexes = jdbc.queryForList("""
                SELECT DISTINCT index_name FROM information_schema.statistics
                WHERE table_schema=DATABASE() AND index_name IN (
                  'uk_user_benefit_fulfillment','uk_draw_chance_account_user',
                  'uk_draw_chance_ledger_business','uk_draw_chance_reservation_request',
                  'uk_lottery_reward_quarantine_event')
                """, String.class);
        assertThat(indexes).hasSize(5);
    }

    @Test
    void rejectsNegativeBalancesAndDuplicateReservationIdentity() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO draw_chance_account(user_id,available_balance,reserved_balance,version)
                VALUES(900000001,-1,0,0)
                """)).isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("""
                INSERT INTO draw_chance_reservation
                  (request_id,user_id,activity_id,draw_date,draw_count,bonus_reserved,status)
                VALUES('PHASE5-SCHEMA-R1',1,1,CURRENT_DATE,1,0,'RESERVED')
                """);
        try {
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO draw_chance_reservation
                      (request_id,user_id,activity_id,draw_date,draw_count,bonus_reserved,status)
                    VALUES('PHASE5-SCHEMA-R1',2,1,CURRENT_DATE,1,0,'RESERVED')
                    """)).isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbc.update("DELETE FROM draw_chance_reservation WHERE request_id='PHASE5-SCHEMA-R1'");
        }
    }
}
