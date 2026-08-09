package com.dongqh.luckyhub.lottery.service;

import com.dongqh.luckyhub.auth.context.LoginContext;
import com.dongqh.luckyhub.auth.model.LoginPrincipal;
import com.dongqh.luckyhub.drawchance.service.DrawChanceService;
import com.dongqh.luckyhub.lottery.dto.DrawCommand;
import com.dongqh.luckyhub.lottery.enums.DrawOrderStatus;
import com.dongqh.luckyhub.lottery.enums.DrawResultType;
import com.dongqh.luckyhub.lottery.quota.DrawQuotaKeys;
import com.dongqh.luckyhub.lottery.quota.DrawQuotaService;
import com.dongqh.luckyhub.lottery.quota.QuotaReservationRequest;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class LotteryOrchestrationIntegrationTests {
    private static final long USER_ID = 987654321L;
    @Autowired private LotteryService lotteryService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private DrawQuotaService quotaService;
    @Autowired private DrawChanceService drawChanceService;
    private String requestId;
    private Long activityId;
    private DrawOrderView result;
    private LocalDate retainedQuotaDate;
    private LocalDate originalQuotaDate;
    private boolean insertedUser;

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
            jdbcTemplate.update("DELETE FROM draw_chance_ledger WHERE user_id=?", USER_ID);
            jdbcTemplate.update("DELETE FROM draw_chance_reservation WHERE request_id=?", requestId);
            jdbcTemplate.update("DELETE FROM draw_chance_account WHERE user_id=?", USER_ID);
        }
        if (result != null) {
            redisTemplate.delete(DrawQuotaKeys.quota(activityId, USER_ID, result.drawDate()));
        }
        if (retainedQuotaDate != null && activityId != null) {
            redisTemplate.delete(DrawQuotaKeys.quota(activityId, USER_ID, retainedQuotaDate));
        }
        if (originalQuotaDate != null && activityId != null) {
            redisTemplate.delete(DrawQuotaKeys.quota(activityId, USER_ID, originalQuotaDate));
        }
        if (activityId != null) {
            jdbcTemplate.update("DELETE FROM marketing_activity WHERE id = ?", activityId);
        }
        if (insertedUser) jdbcTemplate.update("DELETE FROM sys_user WHERE id=?", USER_ID);
    }

    @Test
    void nineRewardedChancesExtendOneFreeDailyChanceToARealTenDraw() {
        jdbcTemplate.update("INSERT INTO sys_user(id,username,password,nickname,status) VALUES(?,?,?,?,1)",
                USER_ID, "rewarded-" + UUID.randomUUID(), "x", "奖励次数集成测试");
        insertedUser = true;
        activityId = insertNoWinActivity();
        jdbcTemplate.update("UPDATE marketing_activity SET daily_limit=1 WHERE id=?", activityId);
        requestId = UUID.randomUUID().toString();
        drawChanceService.credit(USER_ID, "integration-reward-" + requestId, 9);
        LoginContext.set(new LoginPrincipal(USER_ID, "integration", "session"));

        result = lotteryService.draw(new DrawCommand(requestId, activityId, 10));

        assertThat(result.status()).isEqualTo(DrawOrderStatus.SUCCESS);
        assertThat(result.results()).hasSize(10);
        assertThat(drawChanceService.get(USER_ID).availableBalance()).isZero();
        assertThat(drawChanceService.get(USER_ID).reservedBalance()).isEqualTo(9);
        assertThat(redisTemplate.opsForValue().get(
                DrawQuotaKeys.quota(activityId, USER_ID, result.drawDate()))).isEqualTo("10");
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

    @Test
    void releasedRedisReservationCannotBypassFinalStateAfterMidnightAndPolicyChange() {
        activityId = insertNoWinActivity();
        requestId = UUID.randomUUID().toString();
        retainedQuotaDate = LocalDate.now().minusDays(1);
        LoginContext.set(new LoginPrincipal(USER_ID, "integration", "session"));
        originalQuotaDate = quotaService.reserve(
                new QuotaReservationRequest(requestId, activityId, USER_ID, 10, 10)).drawDate();
        quotaService.release(requestId);
        redisTemplate.opsForHash().put(DrawQuotaKeys.reservation(requestId), "drawDate",
                DateTimeFormatter.BASIC_ISO_DATE.format(retainedQuotaDate));
        jdbcTemplate.update("UPDATE marketing_activity SET daily_limit = 1 WHERE id = ?", activityId);

        assertThatThrownBy(() -> result = lotteryService.draw(new DrawCommand(requestId, activityId, 10)))
                .isInstanceOfSatisfying(com.dongqh.luckyhub.common.exception.BusinessException.class,
                        error -> assertThat(error.getErrorCode())
                                .isEqualTo(com.dongqh.luckyhub.lottery.enums.LotteryErrorCode.DRAW_ORDER_FAILED));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lottery_draw_order WHERE request_id = ?", Integer.class, requestId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lottery_draw_record WHERE request_id = ?", Integer.class, requestId))
                .isZero();
        assertThat(redisTemplate.opsForHash().get(
                DrawQuotaKeys.reservation(requestId), "status")).isEqualTo("RELEASED");
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
