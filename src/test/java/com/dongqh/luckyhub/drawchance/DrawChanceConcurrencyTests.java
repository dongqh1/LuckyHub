package com.dongqh.luckyhub.drawchance;

import com.dongqh.luckyhub.drawchance.dto.DrawChanceReservationCommand;
import com.dongqh.luckyhub.drawchance.service.DrawChanceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DrawChanceConcurrencyTests {
    @Autowired DrawChanceService service;
    @Autowired JdbcTemplate jdbc;

    @Test
    void twentyConcurrentReservationsNeverOverdrawTenChances() throws Exception {
        String prefix = "p5-concurrent-" + UUID.randomUUID();
        jdbc.update("INSERT INTO sys_user(username,password,nickname,status) VALUES(?,?,?,1)", prefix, "x", "并发测试");
        long userId = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, prefix);
        try {
            service.credit(userId, prefix + "-reward", 10);
            var executor = Executors.newFixedThreadPool(10);
            try {
                var futures = java.util.stream.IntStream.range(0, 20).mapToObj(index -> executor.submit(() ->
                        service.reserve(new DrawChanceReservationCommand(prefix + "-" + index,
                                88L, userId, 1, LocalDate.of(2026, 8, 9))).bonusReserved())).toList();
                long reserved = 0;
                for (var future : futures) reserved += future.get();
                assertThat(reserved).isEqualTo(10);
            } finally {
                executor.shutdownNow();
            }
            var account = service.get(userId);
            assertThat(account.availableBalance()).isZero();
            assertThat(account.reservedBalance()).isEqualTo(10);
        } finally {
            jdbc.update("DELETE FROM draw_chance_ledger WHERE user_id=?", userId);
            jdbc.update("DELETE FROM draw_chance_reservation WHERE user_id=?", userId);
            jdbc.update("DELETE FROM draw_chance_account WHERE user_id=?", userId);
            jdbc.update("DELETE FROM sys_user WHERE id=?", userId);
        }
    }
}
