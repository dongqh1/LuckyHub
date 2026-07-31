package com.dongqh.luckyhub.lottery.service;

import com.dongqh.luckyhub.lottery.config.LotteryProperties;
import com.dongqh.luckyhub.lottery.entity.LotteryDrawOrder;
import com.dongqh.luckyhub.lottery.enums.DrawOrderStatus;
import com.dongqh.luckyhub.lottery.mapper.LotteryDrawOrderMapper;
import com.dongqh.luckyhub.lottery.model.ReconciliationResult;
import com.dongqh.luckyhub.lottery.quota.DrawQuotaKeys;
import com.dongqh.luckyhub.lottery.quota.DrawQuotaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

@Service
public class LotteryReconciliationServiceImpl implements LotteryReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(LotteryReconciliationServiceImpl.class);
    private static final String PROCESSING_TIMEOUT = "PROCESSING_TIMEOUT";

    private final StringRedisTemplate redisTemplate;
    private final LotteryDrawOrderMapper orderMapper;
    private final DrawOrderLifecycleService lifecycleService;
    private final DrawQuotaService quotaService;
    private final Duration processingTimeout;
    private final ZoneId zoneId;
    private final int batchSize;

    @Autowired
    public LotteryReconciliationServiceImpl(
            StringRedisTemplate redisTemplate,
            LotteryDrawOrderMapper orderMapper,
            DrawOrderLifecycleService lifecycleService,
            DrawQuotaService quotaService,
            LotteryProperties properties,
            @Value("${luckyhub.lottery.reconcile-batch-size:100}") int batchSize) {
        this(redisTemplate, orderMapper, lifecycleService, quotaService,
                properties.processingTimeout(), properties.zoneId(), batchSize);
    }

    public LotteryReconciliationServiceImpl(
            StringRedisTemplate redisTemplate,
            LotteryDrawOrderMapper orderMapper,
            DrawOrderLifecycleService lifecycleService,
            DrawQuotaService quotaService,
            Duration processingTimeout,
            ZoneId zoneId,
            int batchSize) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate);
        this.orderMapper = Objects.requireNonNull(orderMapper);
        this.lifecycleService = Objects.requireNonNull(lifecycleService);
        this.quotaService = Objects.requireNonNull(quotaService);
        this.processingTimeout = Objects.requireNonNull(processingTimeout);
        this.zoneId = Objects.requireNonNull(zoneId);
        if (processingTimeout.isNegative() || processingTimeout.isZero()) {
            throw new IllegalArgumentException("processingTimeout must be positive");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        this.batchSize = batchSize;
    }

    @Override
    public ReconciliationResult reconcileExpiredReservations(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        Set<String> due = redisTemplate.opsForZSet().rangeByScore(
                DrawQuotaKeys.reservationTimeouts(), 0, now.toEpochMilli(), 0, batchSize);
        if (due == null) {
            due = Collections.emptySet();
        }

        MutableResult result = new MutableResult(due.size());
        for (String requestId : due) {
            try {
                reconcileOne(requestId, now, result);
            } catch (RuntimeException error) {
                result.failed++;
                log.warn("Lottery reservation reconciliation failed, requestId={}", requestId, error);
                try {
                    // A permanently damaged member must not occupy every bounded batch
                    // and starve healthy members. Keep it recoverable, but retry later.
                    redisTemplate.opsForZSet().add(DrawQuotaKeys.reservationTimeouts(), requestId,
                            now.plus(processingTimeout).toEpochMilli());
                } catch (RuntimeException rescheduleError) {
                    log.warn("Failed to defer lottery reconciliation retry, requestId={}",
                            requestId, rescheduleError);
                }
            }
        }
        return result.snapshot();
    }

    private void reconcileOne(String requestId, Instant now, MutableResult result) {
        String reservationStatus = reservationStatus(requestId);
        if ("CONFIRMED".equals(reservationStatus)) {
            quotaService.confirm(requestId);
            return;
        }
        if ("RELEASED".equals(reservationStatus)) {
            quotaService.release(requestId);
            return;
        }

        LotteryDrawOrder order = orderMapper.selectByRequestId(requestId);
        if (order == null || order.getStatus() == DrawOrderStatus.FAILED) {
            quotaService.release(requestId);
            result.released++;
            return;
        }
        if (order.getStatus() == DrawOrderStatus.SUCCESS) {
            quotaService.confirm(requestId);
            result.confirmed++;
            return;
        }
        if (order.getStatus() != DrawOrderStatus.PROCESSING) {
            throw new IllegalStateException("Unsupported draw order status: " + order.getStatus());
        }

        Instant deadline = deadline(order);
        if (deadline.isAfter(now)) {
            redisTemplate.opsForZSet().add(
                    DrawQuotaKeys.reservationTimeouts(), requestId, deadline.toEpochMilli());
            result.deferred++;
            return;
        }

        lifecycleService.markFailedAndRequestRelease(
                order, PROCESSING_TIMEOUT, LocalDateTime.ofInstant(now, zoneId));
        LotteryDrawOrder finalOrder = orderMapper.selectByRequestId(requestId);
        if (finalOrder == null) {
            quotaService.release(requestId);
            result.released++;
        } else if (finalOrder.getStatus() == DrawOrderStatus.SUCCESS) {
            quotaService.confirm(requestId);
            result.confirmed++;
        } else if (finalOrder.getStatus() == DrawOrderStatus.FAILED
                && PROCESSING_TIMEOUT.equals(finalOrder.getFailReason())) {
            // The same transaction that won the conditional state change appended a
            // DRAW_RELEASE_REQUESTED outbox event. Keep the ZSet member as a fallback;
            // the event consumer normally releases it first and atomically removes it.
            result.timedOut++;
        } else if (finalOrder.getStatus() == DrawOrderStatus.FAILED) {
            quotaService.release(requestId);
            result.released++;
        } else {
            // Another worker may still own an in-flight transition. Revisit safely.
            result.deferred++;
        }
    }

    private Instant deadline(LotteryDrawOrder order) {
        if (order.getCreatedAt() == null) {
            throw new IllegalStateException("PROCESSING order has no createdAt");
        }
        return order.getCreatedAt().atZone(zoneId).toInstant().plus(processingTimeout);
    }

    private String reservationStatus(String requestId) {
        Object status = redisTemplate.opsForHash().get(
                DrawQuotaKeys.reservation(requestId), "status");
        return status == null ? null : status.toString();
    }

    private static final class MutableResult {
        private final int scanned;
        private int confirmed;
        private int released;
        private int timedOut;
        private int deferred;
        private int failed;

        private MutableResult(int scanned) {
            this.scanned = scanned;
        }

        private ReconciliationResult snapshot() {
            return new ReconciliationResult(scanned, confirmed, released, timedOut, deferred, failed);
        }
    }
}
