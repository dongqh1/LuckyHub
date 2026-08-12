package com.dongqh.luckyhub.shipping.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.fulfillment.enums.FulfillmentStatus;
import com.dongqh.luckyhub.fulfillment.service.FulfillmentTaskService;
import com.dongqh.luckyhub.fulfillment.vo.FulfillmentTaskView;
import com.dongqh.luckyhub.shipping.dto.ShippingOrderQuery;
import com.dongqh.luckyhub.shipping.entity.ShippingAddressSnapshot;
import com.dongqh.luckyhub.shipping.entity.ShippingOrder;
import com.dongqh.luckyhub.shipping.enums.ShippingErrorCode;
import com.dongqh.luckyhub.shipping.enums.ShippingStatus;
import com.dongqh.luckyhub.shipping.mapper.ShippingAddressSnapshotMapper;
import com.dongqh.luckyhub.shipping.mapper.ShippingOrderMapper;
import com.dongqh.luckyhub.shipping.service.ShippingAdminService;
import com.dongqh.luckyhub.shipping.service.ShippingOrderService;
import com.dongqh.luckyhub.shipping.service.ShippingProjectionWorker;
import com.dongqh.luckyhub.shipping.vo.ShippingOrderView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class ShippingAdminServiceImpl implements ShippingAdminService {
    private static final Logger log = LoggerFactory.getLogger(ShippingAdminServiceImpl.class);
    private static final int MAX_SCAN = 100;
    private static final Pattern UNSAFE_NOTE = Pattern.compile(
            "(?i)(\\b(?:exception|payload|ciphertext|secret|authorization|provider response)\\b|[\\w.+-]+@[\\w.-]+|1[3-9]\\d{9}|[{}\\r\\n])");

    private final ShippingOrderMapper orders;
    private final ShippingAddressSnapshotMapper snapshots;
    private final FulfillmentTaskService fulfillment;
    private final ShippingOrderService shippingOrders;
    private final ShippingProjectionWorker projectionWorker;

    public ShippingAdminServiceImpl(ShippingOrderMapper orders, ShippingAddressSnapshotMapper snapshots,
                                    FulfillmentTaskService fulfillment, ShippingOrderService shippingOrders) {
        this(orders, snapshots, fulfillment, shippingOrders, null);
    }

    @Autowired
    public ShippingAdminServiceImpl(ShippingOrderMapper orders, ShippingAddressSnapshotMapper snapshots,
                                    FulfillmentTaskService fulfillment, ShippingOrderService shippingOrders,
                                    ShippingProjectionWorker projectionWorker) {
        this.orders = orders;
        this.snapshots = snapshots;
        this.fulfillment = fulfillment;
        this.shippingOrders = shippingOrders;
        this.projectionWorker = projectionWorker;
    }

    @Override
    public PageResponse<ShippingOrderView> page(ShippingOrderQuery query) {
        Page<ShippingOrder> page = orders.selectPage(new Page<>(query.getPage(), query.getSize()),
                new LambdaQueryWrapper<ShippingOrder>()
                        .eq(query.getStatus() != null, ShippingOrder::getStatus, query.getStatus())
                        .eq(query.getSourceType() != null, ShippingOrder::getSourceType, query.getSourceType())
                        .eq(hasText(query.getSourceId()), ShippingOrder::getSourceId, trimmed(query.getSourceId()))
                        .eq(query.getTargetUserId() != null, ShippingOrder::getTargetUserId, query.getTargetUserId())
                        .eq(hasText(query.getWaybillNo()), ShippingOrder::getWaybillNo, trimmed(query.getWaybillNo()))
                        .orderByDesc(ShippingOrder::getCreatedAt).orderByDesc(ShippingOrder::getId));
        return new PageResponse<>(page.getRecords().stream().map(this::view).toList(), page.getTotal(),
                page.getCurrent(), page.getSize(), page.getPages());
    }

    @Override
    public ShippingOrderView get(String shippingNo) {
        ShippingOrder order = orders.selectByShippingNo(requiredNo(shippingNo));
        if (order == null) throw error(ShippingErrorCode.SHIPPING_NOT_FOUND);
        return view(order);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ShippingOrderView retry(String shippingNo, long operatorId, String note) {
        ShippingOrder order = lock(shippingNo);
        if (order.getStatus() != ShippingStatus.FAILED || order.getFulfillmentNo() == null) {
            throw error(ShippingErrorCode.SHIPPING_STATE_CONFLICT);
        }
        FulfillmentTaskView task = fulfillment.get(order.getFulfillmentNo());
        if (task.status() != FulfillmentStatus.QUARANTINED) {
            throw error(ShippingErrorCode.SHIPPING_STATE_CONFLICT);
        }
        fulfillment.retryQuarantined(order.getFulfillmentNo(), positiveOperator(operatorId), safeNote(note));
        shippingOrders.projectFulfillmentState(order.getFulfillmentNo());
        return get(order.getShippingNo());
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ShippingOrderView terminate(String shippingNo, long operatorId, String note) {
        ShippingOrder order = lock(shippingNo);
        if (order.getFulfillmentNo() == null || order.getStatus() == ShippingStatus.SHIPPED
                || order.getStatus() == ShippingStatus.IN_TRANSIT || order.getStatus() == ShippingStatus.DELIVERED
                || order.getStatus() == ShippingStatus.TERMINATED) {
            throw error(ShippingErrorCode.SHIPPING_STATE_CONFLICT);
        }
        FulfillmentTaskView task = fulfillment.get(order.getFulfillmentNo());
        if (task.status() == FulfillmentStatus.PROCESSING || task.status() == FulfillmentStatus.SUCCEEDED
                || task.status() == FulfillmentStatus.TERMINATED) {
            throw error(ShippingErrorCode.SHIPPING_STATE_CONFLICT);
        }
        fulfillment.terminate(order.getFulfillmentNo(), positiveOperator(operatorId), safeNote(note));
        shippingOrders.projectFulfillmentState(order.getFulfillmentNo());
        return get(order.getShippingNo());
    }

    @Override
    public int projectPending() {
        List<Long> candidates = orders.selectProjectionCandidateIds(MAX_SCAN);
        int projected = 0;
        for (Long id : candidates.stream().sorted().limit(MAX_SCAN).toList()) {
            try {
                if (projectionWorker.projectOne(id)) projected++;
            } catch (RuntimeException ignored) {
                log.warn("单条物流状态投影失败 shippingOrderId={}", id);
            }
        }
        return projected;
    }

    private ShippingOrder lock(String shippingNo) {
        ShippingOrder order = orders.lockByShippingNo(requiredNo(shippingNo));
        if (order == null) throw error(ShippingErrorCode.SHIPPING_NOT_FOUND);
        return order;
    }

    private ShippingOrderView view(ShippingOrder order) {
        ShippingAddressSnapshot snapshot = snapshots.selectById(order.getAddressSnapshotId());
        return new ShippingOrderView(order.getId(), order.getShippingNo(), order.getSourceType(), order.getSourceId(),
                order.getTargetUserId(), order.getAddressSnapshotId(), order.getSkuCode(), order.getProductName(),
                order.getImageUrl(), order.getQuantity(), order.getFulfillmentNo(), order.getCarrierCode(),
                order.getCarrierName(), order.getWaybillNo(), order.getStatus(), order.getLastErrorCode(),
                order.getLastErrorMessage(), order.getShippedAt(), order.getDeliveredAt(), order.getFailedAt(),
                order.getTerminatedAt(), order.getCreatedAt(), order.getUpdatedAt(),
                snapshot == null ? null : snapshot.getReceiverMasked(),
                snapshot == null ? null : snapshot.getPhoneMasked(),
                snapshot == null ? null : snapshot.getRegionMasked());
    }

    private String requiredNo(String value) {
        String normalized = trimmed(value);
        if (normalized == null || normalized.isBlank() || normalized.length() > 64) {
            throw error(ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        }
        return normalized;
    }

    private long positiveOperator(long value) {
        if (value <= 0) throw error(ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        return value;
    }

    private String safeNote(String note) {
        String normalized = trimmed(note);
        if (normalized == null || normalized.isBlank()) return null;
        if (UNSAFE_NOTE.matcher(normalized.toLowerCase(Locale.ROOT)).find()) {
            throw error(ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        }
        return normalized.substring(0, Math.min(500, normalized.length()));
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private String trimmed(String value) { return value == null ? null : value.trim(); }
    private BusinessException error(ShippingErrorCode code) { return new BusinessException(code); }
}
