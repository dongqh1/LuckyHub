package com.dongqh.luckyhub.shipping.integration;

import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.fulfillment.enums.FulfillmentType;
import com.dongqh.luckyhub.fulfillment.model.FulfillmentClaim;
import com.dongqh.luckyhub.fulfillment.model.LogisticsFulfillmentPayload;
import com.dongqh.luckyhub.integration.gateway.LogisticsCreateRequest;
import com.dongqh.luckyhub.shipping.crypto.AddressCipher;
import com.dongqh.luckyhub.shipping.entity.ShippingAddressSnapshot;
import com.dongqh.luckyhub.shipping.entity.ShippingOrder;
import com.dongqh.luckyhub.shipping.enums.ShippingErrorCode;
import com.dongqh.luckyhub.shipping.mapper.ShippingOrderMapper;
import com.dongqh.luckyhub.shipping.service.ShippingAddressSnapshotService;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class LogisticsRequestAssemblerImpl implements LogisticsRequestAssembler {
    private final ShippingOrderMapper orders;
    private final ShippingAddressSnapshotService snapshots;
    private final AddressCipher cipher;

    public LogisticsRequestAssemblerImpl(
            ShippingOrderMapper orders,
            ShippingAddressSnapshotService snapshots,
            AddressCipher cipher
    ) {
        this.orders = orders;
        this.snapshots = snapshots;
        this.cipher = cipher;
    }

    @Override
    public LogisticsCreateRequest assemble(
            FulfillmentClaim claim,
            LogisticsFulfillmentPayload payload
    ) {
        ShippingOrder order = orders.selectById(payload.shippingOrderId());
        if (claim == null || claim.fulfillmentType() != FulfillmentType.LOGISTICS
                || order == null
                || !Objects.equals(order.getFulfillmentNo(), claim.fulfillmentNo())
                || !Objects.equals(order.getTargetUserId(), claim.targetUserId())
                || !Objects.equals(order.getSkuCode(), payload.skuCode())
                || !Objects.equals(order.getQuantity(), payload.quantity())) {
            throw new BusinessException(ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        }
        ShippingAddressSnapshot snapshot = snapshots.require(order.getAddressSnapshotId());
        if (!Objects.equals(snapshot.getUserId(), order.getTargetUserId())
                || snapshot.getSourceType() != order.getSourceType()
                || !Objects.equals(snapshot.getSourceId(), order.getSourceId())
                || !Objects.equals(snapshot.getReceiverMasked(), payload.receiverMasked())
                || !Objects.equals(snapshot.getPhoneMasked(), payload.phoneMasked())
                || !Objects.equals(snapshot.getRegionMasked(), payload.regionMasked())) {
            throw new BusinessException(ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        }
        return new LogisticsCreateRequest(
                claim.fulfillmentNo(), claim.targetUserId(), order.getId(),
                payload.skuCode(), payload.quantity(),
                cipher.decrypt(snapshot.getReceiverCiphertext()),
                cipher.decrypt(snapshot.getPhoneCiphertext()),
                cipher.decrypt(snapshot.getProvinceCiphertext()),
                cipher.decrypt(snapshot.getCityCiphertext()),
                cipher.decrypt(snapshot.getDistrictCiphertext()),
                cipher.decrypt(snapshot.getDetailCiphertext()),
                payload.receiverMasked(), payload.phoneMasked(), payload.regionMasked());
    }
}
