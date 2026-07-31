package com.dongqh.luckyhub.lottery.lock;

import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.lottery.config.LotteryProperties;
import com.dongqh.luckyhub.lottery.enums.LotteryErrorCode;
import com.dongqh.luckyhub.lottery.quota.DrawQuotaKeys;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedissonDrawLockServiceTests {

    private final RedissonClient redissonClient = mock(RedissonClient.class);
    private final RLock lock = mock(RLock.class);
    private final LotteryProperties properties = new LotteryProperties(
            ZoneId.of("Asia/Shanghai"), Duration.ofMillis(275), Duration.ofMinutes(2),
            Duration.ofSeconds(30), Duration.ofSeconds(60), 100,
            Duration.ofHours(72), Duration.ofSeconds(5), 100
    );
    private final RedissonDrawLockService service = new RedissonDrawLockService(redissonClient, properties);

    @Test
    void executesUnderOwnedLockAndUnlocksInFinally() throws InterruptedException {
        when(redissonClient.getLock(DrawQuotaKeys.drawLock(12L, 34L))).thenReturn(lock);
        when(lock.tryLock(275, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        String result = service.execute(12L, 34L, () -> "done");

        assertThat(result).isEqualTo("done");
        verify(lock).unlock();
    }

    @Test
    void releasesOwnedLockWhenActionFails() throws InterruptedException {
        when(redissonClient.getLock(DrawQuotaKeys.drawLock(12L, 34L))).thenReturn(lock);
        when(lock.tryLock(275, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        assertThatThrownBy(() -> service.execute(12L, 34L, () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);
        verify(lock).unlock();
    }

    @Test
    void timeoutDoesNotExecuteOrUnlockAndBecomesBusinessError() throws InterruptedException {
        when(redissonClient.getLock(DrawQuotaKeys.drawLock(12L, 34L))).thenReturn(lock);
        when(lock.tryLock(275, TimeUnit.MILLISECONDS)).thenReturn(false);
        AtomicBoolean executed = new AtomicBoolean();

        assertThatThrownBy(() -> service.execute(12L, 34L, () -> executed.getAndSet(true)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(LotteryErrorCode.DRAW_LOCK_UNAVAILABLE);
        assertThat(executed).isFalse();
        verify(lock, never()).unlock();
    }

    @Test
    void interruptionRestoresFlagAndBecomesBusinessError() throws InterruptedException {
        when(redissonClient.getLock(DrawQuotaKeys.drawLock(12L, 34L))).thenReturn(lock);
        when(lock.tryLock(275, TimeUnit.MILLISECONDS)).thenThrow(new InterruptedException("stop"));

        try {
            assertThatThrownBy(() -> service.execute(12L, 34L, () -> "ignored"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(error -> ((BusinessException) error).getErrorCode())
                    .isEqualTo(LotteryErrorCode.DRAW_LOCK_UNAVAILABLE);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(lock, never()).unlock();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void doesNotExecuteOrUnlockWhenCurrentThreadDoesNotOwnLock() throws InterruptedException {
        when(redissonClient.getLock(DrawQuotaKeys.drawLock(12L, 34L))).thenReturn(lock);
        when(lock.tryLock(275, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(false);
        AtomicBoolean executed = new AtomicBoolean();

        assertThatThrownBy(() -> service.execute(12L, 34L, () -> executed.getAndSet(true)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(LotteryErrorCode.DRAW_LOCK_UNAVAILABLE);
        assertThat(executed).isFalse();
        verify(lock, never()).unlock();
    }
}
