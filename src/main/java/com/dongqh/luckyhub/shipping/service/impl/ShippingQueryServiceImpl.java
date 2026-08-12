package com.dongqh.luckyhub.shipping.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.shipping.entity.ShippingAddressSnapshot;
import com.dongqh.luckyhub.shipping.entity.ShippingOrder;
import com.dongqh.luckyhub.shipping.enums.ShippingErrorCode;
import com.dongqh.luckyhub.shipping.mapper.ShippingAddressSnapshotMapper;
import com.dongqh.luckyhub.shipping.mapper.ShippingOrderMapper;
import com.dongqh.luckyhub.shipping.mapper.ShippingTrackingEventMapper;
import com.dongqh.luckyhub.shipping.service.ShippingQueryService;
import com.dongqh.luckyhub.shipping.vo.ShippingTrackingView;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class ShippingQueryServiceImpl implements ShippingQueryService {
    private final ShippingOrderMapper orders;
    private final ShippingAddressSnapshotMapper snapshots;
    private final ShippingTrackingEventMapper events;

    public ShippingQueryServiceImpl(
            ShippingOrderMapper orders,
            ShippingAddressSnapshotMapper snapshots,
            ShippingTrackingEventMapper events
    ) {
        this.orders = orders;
        this.snapshots = snapshots;
        this.events = events;
    }

    @Override
    public ShippingTrackingView getForUser(long userId, String shippingNo) {
        String normalized = shippingNo == null ? "" : shippingNo.trim();
        if (normalized.isBlank() || normalized.length() > 64) {
            throw new BusinessException(ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        }
        ShippingOrder order = orders.selectOne(new LambdaQueryWrapper<ShippingOrder>()
                .eq(ShippingOrder::getShippingNo, normalized));
        if (order == null || !Objects.equals(order.getTargetUserId(), userId)) {
            throw new BusinessException(ShippingErrorCode.SHIPPING_NOT_FOUND);
        }
        ShippingAddressSnapshot snapshot = snapshots.selectById(order.getAddressSnapshotId());
        if (snapshot == null || !Objects.equals(snapshot.getUserId(), userId)) {
            throw new BusinessException(ShippingErrorCode.SHIPPING_NOT_FOUND);
        }
        var tracking = events.selectByShippingOrderId(order.getId()).stream()
                .map(event -> new ShippingTrackingView.TrackingEvent(
                        event.getEventType(), event.getEventTime(),
                        event.getLocationSummary(), event.getDescription()))
                .toList();
        return new ShippingTrackingView(
                order.getShippingNo(), order.getStatus(), order.getCarrierCode(), order.getCarrierName(),
                order.getWaybillNo(), order.getProductName(), order.getImageUrl(), order.getQuantity(),
                snapshot.getReceiverMasked(), snapshot.getPhoneMasked(), snapshot.getRegionMasked(), tracking);
    }
}
