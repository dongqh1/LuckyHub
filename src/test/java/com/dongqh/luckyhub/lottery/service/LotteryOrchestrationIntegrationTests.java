package com.dongqh.luckyhub.lottery.service;

import com.dongqh.luckyhub.auth.context.LoginContext;
import com.dongqh.luckyhub.auth.model.LoginPrincipal;
import com.dongqh.luckyhub.lottery.dto.DrawCommand;
import com.dongqh.luckyhub.lottery.enums.DrawOrderStatus;
import com.dongqh.luckyhub.lottery.enums.DrawResultType;
import com.dongqh.luckyhub.lottery.quota.DrawQuotaKeys;
import com.dongqh.luckyhub.lottery.vo.DrawOrderView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LotteryOrchestrationIntegrationTests {
    private static final long USER_ID = 987654321L;
    @Autowired private LotteryService lotteryService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private StringRedisTemplate redisTemplate;
    private String requestId;
    private Long activityId;
    private DrawOrderView result;

    @AfterEach
    void cleanUpExactRows() {
        LoginContext.clear();
        if (requestId != null) {
            jdbcTemplate.update("DELETE FROM message_outbox WHERE aggregate_id IN "
                    + "(SELECT CAST(id AS CHAR) FROM lottery_draw_order WHERE request_id = ?)", requestId);
            jdbcTemplate.update("DELETE FROM lottery_draw_record WHERE request_id = ?", requestId);
            jdbcTemplate.update("DELETE FROM lottery_draw_order WHERE request_id = ?", requestId);
            redisTemplate.delete(DrawQuotaKeys.reservation(requestId));
            redisTemplate.opsForZSet().remove(DrawQuotaKeys.reservationTimeouts(), requestId);
        }
        if (result != null) {
            redisTemplate.delete(DrawQuotaKeys.quota(activityId, USER_ID, result.drawDate()));
        }
        if (activityId != null) {
            jdbcTemplate.update("DELETE FROM marketing_activity WHERE id = ?", activityId);
        }
    }

    @Test
    void realRedisAndMysqlTenDrawIsAtomicIdempotentAndReturnsTenExactResults() {
        activityId = insertNoWinActivity();
        requestId = UUID.randomUUID().toString();
        LoginContext.set(new LoginPrincipal(USER_ID, "integration", "session"));
        DrawCommand command = new DrawCommand(requestId, activityId, 10);

        result = lotteryService.draw(command);
        DrawOrderView retry = lotteryService.draw(command);

        assertThat(result.status()).isEqualTo(DrawOrderStatus.SUCCESS);
        assertThat(result.results()).hasSize(10)
                .allMatch(item -> item.resultType() == DrawResultType.NO_WIN);
        assertThat(retry).isEqualTo(result);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lottery_draw_order WHERE request_id = ?", Integer.class, requestId))
                .isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lottery_draw_record WHERE request_id = ?", Integer.class, requestId))
                .isEqualTo(10);
        assertThat(redisTemplate.opsForValue().get(
                DrawQuotaKeys.quota(activityId, USER_ID, result.drawDate()))).isEqualTo("10");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM message_outbox WHERE aggregate_id = ? AND event_type = 'DRAW_CONFIRMED'",
                Integer.class, Long.toString(result.orderId()))).isOne();
    }

    private long insertNoWinActivity() {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO marketing_activity
                        (activity_name, status, start_time, end_time, daily_limit,
                         no_win_weight, created_by)
                    VALUES (?, 'RUNNING', NOW(3) - INTERVAL 1 HOUR,
                            NOW(3) + INTERVAL 1 HOUR, 10, 100, 1)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, "task10-real-" + UUID.randomUUID());
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }
}
