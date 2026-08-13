package com.dongqh.luckyhub.drawchance;

import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.drawchance.dto.DrawChanceReservationCommand;
import com.dongqh.luckyhub.drawchance.enums.DrawChanceReservationStatus;
import com.dongqh.luckyhub.drawchance.service.DrawChanceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "luckyhub.lottery.reconciliation-enabled=false")
class DrawChanceServiceTests {
    @Autowired DrawChanceService service;
    @Autowired JdbcTemplate jdbc;
    long userId;
    final String prefix = "p5-chance-" + UUID.randomUUID();

    @BeforeEach
    void createUser() {
        jdbc.update("INSERT INTO sys_user(username,password,nickname,status) VALUES(?,?,?,1)",
                prefix, "x", "次数测试");
        userId = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, prefix);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM draw_chance_ledger WHERE user_id=?", userId);
        jdbc.update("DELETE FROM draw_chance_reservation WHERE user_id=?", userId);
        jdbc.update("DELETE FROM draw_chance_account WHERE user_id=?", userId);
        jdbc.update("DELETE FROM lottery_draw_order WHERE request_id LIKE ?", prefix + "%");
        jdbc.update("DELETE FROM sys_user WHERE id=?", userId);
    }

    @Test
    void creditIsIdempotentAndRejectsChangedIdentity() {
        assertThat(service.credit(userId, prefix + "-reward", 3).availableBalance()).isEqualTo(3);
        assertThat(service.credit(userId, prefix + "-reward", 3).availableBalance()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM draw_chance_ledger WHERE user_id=?", Integer.class, userId)).isOne();

        assertThatThrownBy(() -> service.credit(userId, prefix + "-reward", 4))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void reserveMovesBonusAndReleaseOrConfirmSettlesExactlyOnce() {
        service.credit(userId, prefix + "-reward", 3);
        var first = service.reserve(command(prefix + "-r1", 10));
        assertThat(first.bonusReserved()).isEqualTo(3);
        assertThat(first.cumulativeBonusForDate()).isEqualTo(3);
        assertThat(service.get(userId).availableBalance()).isZero();
        assertThat(service.get(userId).reservedBalance()).isEqualTo(3);

        var duplicate = service.reserve(command(prefix + "-r1", 10));
        assertThat(duplicate.duplicate()).isTrue();
        service.release(first.requestId());
        service.release(first.requestId());
        assertThat(service.get(userId).availableBalance()).isEqualTo(3);
        assertThat(service.get(userId).reservedBalance()).isZero();

        var second = service.reserve(command(prefix + "-r2", 1));
        service.confirm(second.requestId());
        service.confirm(second.requestId());
        assertThat(service.get(userId).availableBalance()).isEqualTo(2);
        assertThat(service.get(userId).reservedBalance()).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM draw_chance_reservation WHERE request_id=?",
                String.class, second.requestId())).isEqualTo(DrawChanceReservationStatus.CONFIRMED.name());
        assertThatThrownBy(() -> service.release(second.requestId())).isInstanceOf(BusinessException.class);
    }

    @Test
    void zeroBalanceStillCreatesIdentityCheckedReservation() {
        var result = service.reserve(command(prefix + "-zero", 1));
        assertThat(result.bonusReserved()).isZero();
        assertThat(result.status()).isEqualTo(DrawChanceReservationStatus.RESERVED);
        assertThat(service.reserve(command(prefix + "-zero", 1)).duplicate()).isTrue();
        assertThatThrownBy(() -> service.reserve(new DrawChanceReservationCommand(
                prefix + "-zero", 99L, userId, 10, LocalDate.of(2026, 8, 9))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void staleReconciliationFollowsActualOrderTerminalState() {
        service.credit(userId, prefix + "-reward", 2);
        var success = service.reserve(command(prefix + "-success", 1));
        var failed = service.reserve(command(prefix + "-failed", 1));
        insertOrder(success.requestId(), "SUCCESS");
        insertOrder(failed.requestId(), "FAILED");
        jdbc.update("UPDATE draw_chance_reservation SET created_at='1000-01-01 00:00:00' WHERE user_id=?", userId);

        assertThat(service.reconcileExpired(2, LocalDateTime.now().minusHours(1))).isEqualTo(2);
        assertThat(service.get(userId).availableBalance()).isEqualTo(1);
        assertThat(service.get(userId).reservedBalance()).isZero();
        assertThat(jdbc.queryForList("SELECT status FROM draw_chance_reservation WHERE user_id=? ORDER BY request_id",
                String.class, userId)).containsExactly("RELEASED", "CONFIRMED");
    }

    private void insertOrder(String requestId, String status) {
        jdbc.update("""
                INSERT INTO lottery_draw_order(request_id,user_id,activity_id,draw_count,draw_date,status)
                VALUES(?,?,88,1,'2026-08-09',?)
                """, requestId, userId, status);
    }

    private DrawChanceReservationCommand command(String requestId, int count) {
        return new DrawChanceReservationCommand(requestId, 88L, userId, count, LocalDate.of(2026, 8, 9));
    }
}
