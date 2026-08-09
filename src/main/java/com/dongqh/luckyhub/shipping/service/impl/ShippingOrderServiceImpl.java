package com.dongqh.luckyhub.shipping.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.fulfillment.dto.CreateFulfillmentTaskCommand;
import com.dongqh.luckyhub.fulfillment.enums.FulfillmentStatus;
import com.dongqh.luckyhub.fulfillment.enums.FulfillmentType;
import com.dongqh.luckyhub.fulfillment.model.LogisticsFulfillmentPayload;
import com.dongqh.luckyhub.fulfillment.service.FulfillmentTaskService;
import com.dongqh.luckyhub.fulfillment.vo.FulfillmentTaskView;
import com.dongqh.luckyhub.shipping.entity.ShippingAddressSnapshot;
import com.dongqh.luckyhub.shipping.entity.ShippingOrder;
import com.dongqh.luckyhub.shipping.enums.ShippingErrorCode;
import com.dongqh.luckyhub.shipping.enums.ShippingSourceType;
import com.dongqh.luckyhub.shipping.enums.ShippingStatus;
import com.dongqh.luckyhub.shipping.mapper.ShippingOrderMapper;
import com.dongqh.luckyhub.shipping.model.CreateShippingOrderCommand;
import com.dongqh.luckyhub.shipping.service.ShippingAddressSnapshotService;
import com.dongqh.luckyhub.shipping.service.ShippingOrderService;
import com.dongqh.luckyhub.shipping.vo.ShippingOrderView;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

@Service
public class ShippingOrderServiceImpl implements ShippingOrderService {
    private static final int MAX_ATTEMPTS = 5;
    private final ShippingOrderMapper mapper;
    private final ShippingAddressSnapshotService snapshots;
    private final FulfillmentTaskService fulfillmentTasks;
    private final JdbcTemplate jdbc;

    public ShippingOrderServiceImpl(
            ShippingOrderMapper mapper,
            ShippingAddressSnapshotService snapshots,
            FulfillmentTaskService fulfillmentTasks,
            JdbcTemplate jdbc
    ) {
        this.mapper = mapper;
        this.snapshots = snapshots;
        this.fulfillmentTasks = fulfillmentTasks;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ShippingOrderView create(CreateShippingOrderCommand command) {
        ShippingAddressSnapshot snapshot = snapshots.require(command.addressSnapshotId());
        validateSnapshot(command, snapshot);
        ShippingOrder existing = findBySource(command.sourceType(), command.sourceId());
        if (existing != null) return view(requireEqual(existing, command));

        ShippingOrder order = candidate(command);
        try {
            mapper.insert(order);
        } catch (DuplicateKeyException exception) {
            ShippingOrder winner = findBySource(command.sourceType(), command.sourceId());
            if (winner == null && command.claimRequestId() != null) {
                winner = mapper.selectByClaimRequestId(command.claimRequestId());
            }
            if (winner == null) throw exception;
            return view(requireEqual(winner, command));
        }

        order.setFulfillmentNo("LOGISTICS-" + order.getId());
        order.setStatus(ShippingStatus.FULFILLING);
        if (jdbc.update("""
                UPDATE shipping_order
                SET fulfillment_no=?, status='FULFILLING', version=version+1,
                    updated_at=CURRENT_TIMESTAMP(3)
                WHERE id=? AND fulfillment_no IS NULL AND status='READY'
                """, order.getFulfillmentNo(), order.getId()) != 1) {
            throw error(ShippingErrorCode.SHIPPING_STATE_CONFLICT);
        }
        fulfillmentTasks.create(new CreateFulfillmentTaskCommand(
                order.getFulfillmentNo(), command.sourceType().name(), command.sourceId(),
                FulfillmentType.LOGISTICS, command.targetUserId(),
                new LogisticsFulfillmentPayload(order.getId(), command.skuCode(), command.quantity(),
                        snapshot.getReceiverMasked(), snapshot.getPhoneMasked(), snapshot.getRegionMasked()),
                MAX_ATTEMPTS));
        return view(mapper.selectById(order.getId()));
    }

    @Override
    public ShippingOrderView getForUser(long userId, String shippingNo) {
        ShippingOrder order = mapper.selectOne(new LambdaQueryWrapper<ShippingOrder>()
                .eq(ShippingOrder::getShippingNo, required(shippingNo)));
        if (order == null || !Objects.equals(order.getTargetUserId(), userId)) {
            throw error(ShippingErrorCode.SHIPPING_NOT_FOUND);
        }
        return view(order);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void projectFulfillmentState(String fulfillmentNo) {
        ShippingOrder order = mapper.selectOne(new LambdaQueryWrapper<ShippingOrder>()
                .eq(ShippingOrder::getFulfillmentNo, required(fulfillmentNo)));
        if (order == null) return;
        FulfillmentTaskView task = fulfillmentTasks.get(fulfillmentNo);
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        if (task.status() == FulfillmentStatus.SUCCEEDED) {
            if (order.getStatus() == ShippingStatus.SHIPPED
                    || order.getStatus() == ShippingStatus.IN_TRANSIT
                    || order.getStatus() == ShippingStatus.DELIVERED) return;
            order.setCarrierCode("SIMULATOR");
            order.setCarrierName("模拟物流");
            order.setWaybillNo(bounded(task.externalReference(), 100));
            order.setStatus(ShippingStatus.SHIPPED);
            order.setShippedAt(order.getShippedAt() == null ? now : order.getShippedAt());
            order.setLastErrorCode(null);
            order.setLastErrorMessage(null);
            order.setFailedAt(null);
        } else if (task.status() == FulfillmentStatus.QUARANTINED) {
            if (order.getStatus() != ShippingStatus.FULFILLING) return;
            order.setStatus(ShippingStatus.FAILED);
            order.setLastErrorCode(defaulted(task.lastErrorCode(), "FULFILLMENT_QUARANTINED", 64));
            order.setLastErrorMessage(defaulted(task.lastErrorMessage(), "物流履约已安全隔离", 500));
            order.setFailedAt(now);
        } else if (task.status() == FulfillmentStatus.TERMINATED) {
            if (order.getStatus() == ShippingStatus.SHIPPED
                    || order.getStatus() == ShippingStatus.IN_TRANSIT
                    || order.getStatus() == ShippingStatus.DELIVERED) return;
            order.setStatus(ShippingStatus.TERMINATED);
            order.setLastErrorCode(defaulted(task.lastErrorCode(), "FULFILLMENT_TERMINATED", 64));
            order.setLastErrorMessage(defaulted(task.lastErrorMessage(), "物流履约已安全终止", 500));
            order.setTerminatedAt(now);
        } else if (order.getStatus() == ShippingStatus.FAILED) {
            order.setStatus(ShippingStatus.FULFILLING);
            order.setLastErrorCode(null);
            order.setLastErrorMessage(null);
            order.setFailedAt(null);
        } else {
            return;
        }
        if (jdbc.update("""
                UPDATE shipping_order
                SET carrier_code=?, carrier_name=?, waybill_no=?, status=?,
                    last_error_code=?, last_error_message=?, shipped_at=?, failed_at=?,
                    terminated_at=?, version=version+1, updated_at=CURRENT_TIMESTAMP(3)
                WHERE id=?
                """, order.getCarrierCode(), order.getCarrierName(), order.getWaybillNo(),
                order.getStatus().name(), order.getLastErrorCode(), order.getLastErrorMessage(),
                order.getShippedAt(), order.getFailedAt(), order.getTerminatedAt(), order.getId()) != 1) {
            throw error(ShippingErrorCode.SHIPPING_STATE_CONFLICT);
        }
        projectLotterySource(order);
    }

    private ShippingOrder candidate(CreateShippingOrderCommand command) {
        ShippingOrder order = new ShippingOrder();
        order.setShippingNo("SHIPPING-" + UUID.randomUUID());
        order.setSourceType(command.sourceType());
        order.setSourceId(command.sourceId());
        order.setTargetUserId(command.targetUserId());
        order.setAddressSnapshotId(command.addressSnapshotId());
        order.setSkuCode(command.skuCode());
        order.setProductName(command.productName());
        order.setImageUrl(command.imageUrl());
        order.setQuantity(command.quantity());
        order.setClaimRequestId(command.claimRequestId());
        order.setStatus(ShippingStatus.READY);
        order.setVersion(0);
        return order;
    }

    private void validateSnapshot(CreateShippingOrderCommand command, ShippingAddressSnapshot snapshot) {
        if (!Objects.equals(snapshot.getUserId(), command.targetUserId())
                || snapshot.getSourceType() != command.sourceType()
                || !Objects.equals(snapshot.getSourceId(), command.sourceId())) {
            throw error(ShippingErrorCode.SHIPPING_IDEMPOTENCY_CONFLICT);
        }
    }

    private ShippingOrder requireEqual(ShippingOrder order, CreateShippingOrderCommand command) {
        if (order.getSourceType() != command.sourceType()
                || !Objects.equals(order.getSourceId(), command.sourceId())
                || !Objects.equals(order.getTargetUserId(), command.targetUserId())
                || !Objects.equals(order.getAddressSnapshotId(), command.addressSnapshotId())
                || !Objects.equals(order.getSkuCode(), command.skuCode())
                || !Objects.equals(order.getProductName(), command.productName())
                || !Objects.equals(order.getImageUrl(), command.imageUrl())
                || !Objects.equals(order.getQuantity(), command.quantity())
                || !Objects.equals(order.getClaimRequestId(), command.claimRequestId())) {
            throw error(ShippingErrorCode.SHIPPING_IDEMPOTENCY_CONFLICT);
        }
        return order;
    }

    private ShippingOrder findBySource(ShippingSourceType type, String sourceId) {
        return mapper.selectOne(new LambdaQueryWrapper<ShippingOrder>()
                .eq(ShippingOrder::getSourceType, type)
                .eq(ShippingOrder::getSourceId, sourceId));
    }

    private void projectLotterySource(ShippingOrder order) {
        if (order.getSourceType() != ShippingSourceType.LOTTERY_BENEFIT) return;
        if (order.getStatus() == ShippingStatus.FAILED) {
            jdbc.update("UPDATE user_benefit SET status='FULFILLMENT_FAILED' WHERE id=? AND status IN ('CLAIMED','FULFILLING')",
                    Long.valueOf(order.getSourceId()));
        } else if (order.getStatus() == ShippingStatus.TERMINATED) {
            jdbc.update("UPDATE user_benefit SET status='FULFILLMENT_TERMINATED' WHERE id=? AND status IN ('CLAIMED','FULFILLING','FULFILLMENT_FAILED')",
                    Long.valueOf(order.getSourceId()));
        } else if (order.getStatus() == ShippingStatus.FULFILLING) {
            jdbc.update("UPDATE user_benefit SET status='FULFILLING' WHERE id=? AND status='FULFILLMENT_FAILED'",
                    Long.valueOf(order.getSourceId()));
        }
    }

    private ShippingOrderView view(ShippingOrder order) {
        return new ShippingOrderView(order.getId(), order.getShippingNo(), order.getSourceType(),
                order.getSourceId(), order.getTargetUserId(), order.getAddressSnapshotId(),
                order.getSkuCode(), order.getProductName(), order.getImageUrl(), order.getQuantity(),
                order.getFulfillmentNo(), order.getCarrierCode(), order.getCarrierName(),
                order.getWaybillNo(), order.getStatus(), order.getLastErrorCode(),
                order.getLastErrorMessage(), order.getShippedAt(), order.getDeliveredAt(),
                order.getFailedAt(), order.getTerminatedAt(), order.getCreatedAt(), order.getUpdatedAt());
    }

    private String required(String value) {
        if (value == null || value.isBlank()) throw error(ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        return value.trim();
    }

    private String bounded(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        return normalized.substring(0, Math.min(max, normalized.length()));
    }

    private String defaulted(String value, String fallback, int max) {
        String bounded = bounded(value, max);
        return bounded == null ? fallback : bounded;
    }

    private BusinessException error(ShippingErrorCode code) {
        return new BusinessException(code);
    }
}
