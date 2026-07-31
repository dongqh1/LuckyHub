package com.dongqh.luckyhub.lottery.service;

import com.dongqh.luckyhub.lottery.config.LotteryProperties;
import com.dongqh.luckyhub.lottery.entity.LotteryDrawOrder;
import com.dongqh.luckyhub.lottery.enums.DrawOrderStatus;
import com.dongqh.luckyhub.lottery.lock.DrawLockService;
import com.dongqh.luckyhub.lottery.mapper.LotteryDrawOrderMapper;
import com.dongqh.luckyhub.lottery.model.ReconciliationResult;
import com.dongqh.luckyhub.lottery.quota.DrawQuotaKeys;
import com.dongqh.luckyhub.lottery.quota.DrawQuotaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

@Service
public class LotteryReconciliationServiceImpl implements LotteryReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(LotteryReconciliationServiceImpl.class);
    private static final String PROCESSING_TIMEOUT = "PROCESSING_TIMEOUT";
    private static final DateTimeFormatter DRAW_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final List<Object> RESERVATION_FIELDS = List.of(
            "requestId", "activityId", "userId", "drawCount", "drawDate", "status", "createdAt");

    private final StringRedisTemplate redisTemplate;
    private final LotteryDrawOrderMapper orderMapper;
    private final DrawOrderLifecycleService lifecycleService;
    private final DrawQuotaService quotaService;
    private final DrawLockService lockService;
    private final Duration processingTimeout;
    private final ZoneId zoneId;
    private final int batchSize;

    @Autowired
    public LotteryReconciliationServiceImpl(
            StringRedisTemplate redisTemplate,
            LotteryDrawOrderMapper orderMapper,
            DrawOrderLifecycleService lifecycleService,
            DrawQuotaService quotaService,
            DrawLockService lockService,
            LotteryProperties properties) {
        this(redisTemplate, orderMapper, lifecycleService, quotaService, lockService,
                properties.processingTimeout(), properties.zoneId(), properties.reconcileBatchSize());
    }

    public LotteryReconciliationServiceImpl(
            StringRedisTemplate redisTemplate,
            LotteryDrawOrderMapper orderMapper,
            DrawOrderLifecycleService lifecycleService,
            DrawQuotaService quotaService,
            DrawLockService lockService,
            Duration processingTimeout,
            ZoneId zoneId,
            int batchSize) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate);
        this.orderMapper = Objects.requireNonNull(orderMapper);
        this.lifecycleService = Objects.requireNonNull(lifecycleService);
        this.quotaService = Objects.requireNonNull(quotaService);
        this.lockService = Objects.requireNonNull(lockService);
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
        ReservationSnapshot initial = readReservationOrCleanTerminal(requestId);
        if (initial == null) return;
        lockService.execute(initial.activityId(), initial.userId(), () -> {
            reconcileUnderDrawLock(requestId, initial, now, result);
            return null;
        });
    }

    private void reconcileUnderDrawLock(String requestId, ReservationSnapshot initial,
                                        Instant now, MutableResult result) {
        ReservationSnapshot reservation = readReservationOrCleanTerminal(requestId);
        if (reservation == null) return;
        if (!reservation.sameIdentity(initial)) {
            throw new IllegalStateException("Reservation identity changed while acquiring draw lock");
        }
        if (reservation.status() == ReservationState.CONFIRMED) {
            quotaService.confirm(requestId);
            return;
        }
        if (reservation.status() == ReservationState.RELEASED) {
            quotaService.release(requestId);
            return;
        }

        LotteryDrawOrder order = orderMapper.selectByRequestId(requestId);
        if (order == null) {
            quotaService.release(requestId);
            result.released++;
            return;
        }
        validateOrderIdentity(order, reservation);
        if (order.getStatus() == DrawOrderStatus.FAILED) {
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
            return;
        }
        validateOrderIdentity(finalOrder, reservation);
        if (finalOrder.getStatus() == DrawOrderStatus.SUCCESS) {
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

    private ReservationSnapshot readReservationOrCleanTerminal(String requestId) {
        List<Object> values = redisTemplate.opsForHash().multiGet(
                DrawQuotaKeys.reservation(requestId), RESERVATION_FIELDS);
        String rawStatus = value(values, 5);
        if (values == null || values.stream().allMatch(Objects::isNull)) {
            quotaService.removeTimeout(requestId);
            return null;
        }
        try {
            String storedRequestId = required(value(values, 0), "requestId");
            if (!requestId.equals(storedRequestId) || storedRequestId.length() > 64) {
                throw new IllegalStateException("reservation requestId mismatch");
            }
            long activityId = positiveLong(value(values, 1), "activityId");
            long userId = positiveLong(value(values, 2), "userId");
            int drawCount = Integer.parseInt(required(value(values, 3), "drawCount"));
            if (drawCount != 1 && drawCount != 10) {
                throw new IllegalStateException("invalid reservation drawCount");
            }
            LocalDate drawDate = LocalDate.parse(required(value(values, 4), "drawDate"), DRAW_DATE);
            ReservationState status = ReservationState.valueOf(required(rawStatus, "status"));
            long createdAt = Long.parseLong(required(value(values, 6), "createdAt"));
            if (createdAt <= 0) throw new IllegalStateException("invalid reservation createdAt");
            return new ReservationSnapshot(storedRequestId, activityId, userId, drawCount,
                    drawDate, status, createdAt);
        } catch (RuntimeException invalid) {
            if ("CONFIRMED".equals(rawStatus) || "RELEASED".equals(rawStatus)) {
                quotaService.removeTimeout(requestId);
                return null;
            }
            throw new IllegalStateException("Invalid RESERVED reservation identity", invalid);
        }
    }

    private void validateOrderIdentity(LotteryDrawOrder order, ReservationSnapshot reservation) {
        if (!Objects.equals(order.getRequestId(), reservation.requestId())
                || !Objects.equals(order.getActivityId(), reservation.activityId())
                || !Objects.equals(order.getUserId(), reservation.userId())
                || !Objects.equals(order.getDrawCount(), reservation.drawCount())
                || !Objects.equals(order.getDrawDate(), reservation.drawDate())) {
            throw new IllegalStateException("Reservation and draw order identity mismatch");
        }
    }

    private static String value(List<Object> values, int index) {
        if (values == null || values.size() <= index || values.get(index) == null) return null;
        return values.get(index).toString();
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is missing");
        return value;
    }

    private static long positiveLong(String value, String name) {
        long parsed = Long.parseLong(required(value, name));
        if (parsed <= 0) throw new IllegalStateException(name + " must be positive");
        return parsed;
    }

    private enum ReservationState { RESERVED, CONFIRMED, RELEASED }

    private record ReservationSnapshot(String requestId, long activityId, long userId,
                                       int drawCount, LocalDate drawDate,
                                       ReservationState status, long createdAt) {
        private boolean sameIdentity(ReservationSnapshot other) {
            return requestId.equals(other.requestId)
                    && activityId == other.activityId && userId == other.userId
                    && drawCount == other.drawCount && drawDate.equals(other.drawDate);
        }
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
