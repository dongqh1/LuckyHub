package com.dongqh.luckyhub.shipping.service.impl;

import com.dongqh.luckyhub.benefit.entity.UserBenefit;
import com.dongqh.luckyhub.benefit.enums.BenefitStatus;
import com.dongqh.luckyhub.benefit.mapper.UserBenefitMapper;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.lottery.entity.LotteryDrawRecord;
import com.dongqh.luckyhub.lottery.mapper.LotteryDrawRecordMapper;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import com.dongqh.luckyhub.reward.enums.RewardType;
import com.dongqh.luckyhub.reward.model.ProductRewardPayload;
import com.dongqh.luckyhub.shipping.dto.ClaimPhysicalBenefitCommand;
import com.dongqh.luckyhub.shipping.entity.ShippingAddressSnapshot;
import com.dongqh.luckyhub.shipping.entity.ShippingOrder;
import com.dongqh.luckyhub.shipping.enums.ShippingErrorCode;
import com.dongqh.luckyhub.shipping.enums.ShippingSourceType;
import com.dongqh.luckyhub.shipping.mapper.ShippingOrderMapper;
import com.dongqh.luckyhub.shipping.model.CreateShippingOrderCommand;
import com.dongqh.luckyhub.shipping.service.PhysicalClaimService;
import com.dongqh.luckyhub.shipping.service.ShippingAddressSnapshotService;
import com.dongqh.luckyhub.shipping.service.ShippingOrderService;
import com.dongqh.luckyhub.shipping.vo.ShippingOrderView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

@Service
public class PhysicalClaimServiceImpl implements PhysicalClaimService {
    private final UserBenefitMapper benefits;
    private final LotteryDrawRecordMapper drawRecords;
    private final ShippingAddressSnapshotService snapshots;
    private final ShippingOrderService shippingOrders;
    private final ShippingOrderMapper shippingOrderMapper;
    private final ObjectMapper json;

    public PhysicalClaimServiceImpl(UserBenefitMapper benefits,
                                    LotteryDrawRecordMapper drawRecords,
                                    ShippingAddressSnapshotService snapshots,
                                    ShippingOrderService shippingOrders,
                                    ShippingOrderMapper shippingOrderMapper,
                                    ObjectMapper json) {
        this.benefits = benefits;
        this.drawRecords = drawRecords;
        this.snapshots = snapshots;
        this.shippingOrders = shippingOrders;
        this.shippingOrderMapper = shippingOrderMapper;
        this.json = json;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ShippingOrderView claim(long userId, long benefitId, ClaimPhysicalBenefitCommand command) {
        String requestId = normalizeRequest(command);
        UserBenefit benefit = benefits.selectByIdForUpdate(benefitId);
        if (benefit == null || !Objects.equals(benefit.getUserId(), userId)) {
            throw error(ShippingErrorCode.CLAIM_NOT_ALLOWED);
        }
        ProductRewardPayload product = validateFrozenProduct(benefit);
        LotteryDrawRecord draw = requireDrawIdentity(benefit);

        ShippingOrder existing = shippingOrderMapper.lockBySource(
                ShippingSourceType.LOTTERY_BENEFIT, Long.toString(benefitId));
        if (existing != null) return existing(existing, command.addressId(), requestId, benefit, product);
        if (benefit.getStatus() != BenefitStatus.CLAIM_PENDING || benefit.getShippingOrderId() != null) {
            throw error(ShippingErrorCode.CLAIM_NOT_ALLOWED);
        }
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        if (benefit.getClaimDeadline() == null || !now.isBefore(benefit.getClaimDeadline())) {
            throw error(ShippingErrorCode.CLAIM_EXPIRED);
        }

        String sourceId = Long.toString(benefitId);
        ShippingAddressSnapshot snapshot = snapshots.create(userId, command.addressId(),
                ShippingSourceType.LOTTERY_BENEFIT, sourceId);
        ShippingOrderView order = shippingOrders.create(new CreateShippingOrderCommand(
                ShippingSourceType.LOTTERY_BENEFIT, sourceId, userId, snapshot.getId(),
                product.skuCode(), product.productName(), draw.getPrizeImageUrl(),
                product.quantity(), requestId));
        if (benefits.markClaimed(benefitId, order.id(), now) != 1) {
            throw error(ShippingErrorCode.CLAIM_NOT_ALLOWED);
        }
        return order;
    }

    private ShippingOrderView existing(ShippingOrder order, long addressId, String requestId,
                                       UserBenefit benefit, ProductRewardPayload product) {
        ShippingAddressSnapshot snapshot = snapshots.require(order.getAddressSnapshotId());
        if (!Objects.equals(order.getTargetUserId(), benefit.getUserId())
                || !Objects.equals(order.getClaimRequestId(), requestId)
                || !Objects.equals(snapshot.getAddressId(), addressId)
                || !Objects.equals(order.getSkuCode(), product.skuCode())
                || !Objects.equals(order.getProductName(), product.productName())
                || !Objects.equals(order.getQuantity(), product.quantity())) {
            throw error(ShippingErrorCode.SHIPPING_IDEMPOTENCY_CONFLICT);
        }
        return shippingOrders.getForUser(benefit.getUserId(), order.getShippingNo());
    }

    private ProductRewardPayload validateFrozenProduct(UserBenefit benefit) {
        if (benefit.getPrizeType() != PrizeType.PHYSICAL || benefit.getRewardType() != RewardType.PRODUCT
                || benefit.getRewardTargetId() == null || benefit.getRewardQuantity() == null
                || benefit.getRewardPayload() == null) {
            throw error(ShippingErrorCode.CLAIM_NOT_ALLOWED);
        }
        try {
            ProductRewardPayload product = json.readValue(benefit.getRewardPayload(), ProductRewardPayload.class);
            if (!Objects.equals(benefit.getRewardTargetId(), product.skuId())
                    || benefit.getRewardQuantity() != product.quantity()
                    || !Objects.equals(benefit.getQuantity(), product.quantity())) {
                throw error(ShippingErrorCode.CLAIM_NOT_ALLOWED);
            }
            return product;
        } catch (JacksonException | IllegalArgumentException exception) {
            throw error(ShippingErrorCode.CLAIM_NOT_ALLOWED);
        }
    }

    private LotteryDrawRecord requireDrawIdentity(UserBenefit benefit) {
        LotteryDrawRecord draw = drawRecords.selectById(benefit.getDrawRecordId());
        if (draw == null || !Objects.equals(draw.getUserId(), benefit.getUserId())
                || !Objects.equals(draw.getPrizeId(), benefit.getPrizeId())
                || draw.getPrizeType() != benefit.getPrizeType()
                || !Objects.equals(draw.getRewardDefinitionId(), benefit.getRewardDefinitionId())
                || draw.getRewardType() != benefit.getRewardType()
                || !Objects.equals(draw.getRewardTargetId(), benefit.getRewardTargetId())
                || !Objects.equals(draw.getRewardQuantity(), benefit.getRewardQuantity())
                || !Objects.equals(draw.getRewardPayload(), benefit.getRewardPayload())
                || !Objects.equals(draw.getRewardFingerprint(), benefit.getRewardFingerprint())) {
            throw error(ShippingErrorCode.CLAIM_NOT_ALLOWED);
        }
        return draw;
    }

    private String normalizeRequest(ClaimPhysicalBenefitCommand command) {
        if (command == null || command.addressId() == null || command.addressId() <= 0
                || command.requestId() == null) throw error(ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        String raw = command.requestId().trim();
        try {
            String normalized = UUID.fromString(raw).toString();
            if (!normalized.equalsIgnoreCase(raw)) throw new IllegalArgumentException();
            return normalized;
        } catch (IllegalArgumentException exception) {
            throw error(ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        }
    }

    private BusinessException error(ShippingErrorCode code) {
        return new BusinessException(code);
    }
}
