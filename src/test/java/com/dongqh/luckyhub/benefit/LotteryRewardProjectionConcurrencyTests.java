package com.dongqh.luckyhub.benefit;

import com.dongqh.luckyhub.benefit.service.LotteryRewardProjectionService;
import com.dongqh.luckyhub.rbac.entity.SysUser;
import com.dongqh.luckyhub.rbac.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LotteryRewardProjectionConcurrencyTests {
    @Autowired LotteryRewardProjectionService projection;
    @Autowired SysUserMapper users;
    @Autowired JdbcTemplate jdbc;
    private Long userId;

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM points_ledger");
        jdbc.update("DELETE FROM points_account");
        jdbc.update("DELETE FROM fulfillment_task");
        jdbc.update("DELETE FROM user_benefit");
        if (userId != null) users.deleteById(userId);
    }

    @Test
    void concurrentProjectionCreditsPointsExactlyOnce() throws Exception {
        SysUser user = new SysUser();
        user.setUsername("race-" + UUID.randomUUID().toString().substring(0, 12));
        user.setPassword("x");
        user.setNickname("投影并发用户");
        user.setStatus(1);
        users.insert(user);
        userId = user.getId();
        String no = "LR-RACE-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO user_benefit
                  (draw_record_id,user_id,prize_id,prize_type,reward_definition_id,reward_type,
                   reward_quantity,reward_payload,reward_fingerprint,fulfillment_no,quantity,status,obtained_at)
                VALUES (?,?,?,'POINTS',?,'POINTS',500,CAST('{}' AS JSON),?,?,1,'PENDING',CURRENT_TIMESTAMP(3))
                """, positiveId(), userId, positiveId(), positiveId(), "f".repeat(64), no);
        long benefitId = jdbc.queryForObject("SELECT id FROM user_benefit WHERE fulfillment_no=?", Long.class, no);
        jdbc.update("""
                INSERT INTO fulfillment_task
                  (fulfillment_no,source_type,source_id,fulfillment_type,target_user_id,request_payload,
                   request_fingerprint,status,attempt_count,max_attempts,version)
                VALUES (?,'LOTTERY_BENEFIT',?,'POINTS',?,CAST(? AS JSON),?,'SUCCEEDED',0,5,0)
                """, no, Long.toString(benefitId), userId,
                "{\"points\":500,\"reason\":\"抽奖奖励\"}", "a".repeat(64));

        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(8);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    projection.project(benefitId);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) future.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM points_ledger WHERE business_id=?", Long.class, no)).isOne();
        assertThat(jdbc.queryForObject("SELECT balance FROM points_account WHERE user_id=?", Long.class, userId)).isEqualTo(500L);
        assertThat(jdbc.queryForObject("SELECT status FROM user_benefit WHERE id=?", String.class, benefitId)).isEqualTo("AVAILABLE");
    }

    private long positiveId() {
        long value = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        return value == 0 ? 1 : value;
    }
}
