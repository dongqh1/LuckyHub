package com.dongqh.luckyhub.shipping;

import com.dongqh.luckyhub.benefit.entity.UserBenefit;
import com.dongqh.luckyhub.benefit.enums.BenefitStatus;
import com.dongqh.luckyhub.benefit.mapper.UserBenefitMapper;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.lottery.entity.LotteryDrawRecord;
import com.dongqh.luckyhub.lottery.mapper.LotteryDrawRecordMapper;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import com.dongqh.luckyhub.reward.enums.RewardType;
import com.dongqh.luckyhub.shipping.dto.ClaimPhysicalBenefitCommand;
import com.dongqh.luckyhub.shipping.entity.ShippingAddressSnapshot;
import com.dongqh.luckyhub.shipping.enums.ShippingErrorCode;
import com.dongqh.luckyhub.shipping.enums.ShippingSourceType;
import com.dongqh.luckyhub.shipping.enums.ShippingStatus;
import com.dongqh.luckyhub.shipping.mapper.ShippingOrderMapper;
import com.dongqh.luckyhub.shipping.service.ShippingAddressSnapshotService;
import com.dongqh.luckyhub.shipping.service.ShippingOrderService;
import com.dongqh.luckyhub.shipping.service.impl.PhysicalClaimServiceImpl;
import com.dongqh.luckyhub.shipping.vo.ShippingOrderView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PhysicalBenefitClaimTests {
    UserBenefitMapper benefits = mock(UserBenefitMapper.class);
    LotteryDrawRecordMapper draws = mock(LotteryDrawRecordMapper.class);
    ShippingAddressSnapshotService snapshots = mock(ShippingAddressSnapshotService.class);
    ShippingOrderService orders = mock(ShippingOrderService.class);
    ShippingOrderMapper orderMapper = mock(ShippingOrderMapper.class);
    PhysicalClaimServiceImpl service = new PhysicalClaimServiceImpl(
            benefits, draws, snapshots, orders, orderMapper,
            JsonMapper.builder().findAndAddModules().build());
    String requestId;
    UserBenefit benefit;

    @BeforeEach
    void setUp() {
        requestId = UUID.randomUUID().toString();
        benefit = benefit(LocalDateTime.now().plusDays(1));
        when(benefits.selectByIdForUpdate(31)).thenReturn(benefit);
        when(draws.selectById(21L)).thenReturn(draw(benefit));
    }

    @Test
    void eligibleClaimCreatesSnapshotAndUnifiedShippingThenAttachesIt() {
        ShippingAddressSnapshot snapshot = new ShippingAddressSnapshot();
        snapshot.setId(41L);
        when(snapshots.create(11, 51, ShippingSourceType.LOTTERY_BENEFIT, "31"))
                .thenReturn(snapshot);
        ShippingOrderView expected = view();
        when(orders.create(any())).thenReturn(expected);
        when(benefits.markClaimed(eq(31L), eq(61L), any())).thenReturn(1);

        assertThat(service.claim(11, 31, new ClaimPhysicalBenefitCommand(requestId, 51L)))
                .isEqualTo(expected);
        verify(orders).create(argThat(command -> command.claimRequestId().equals(requestId)
                && command.skuCode().equals("SKU-1") && command.quantity() == 2));
        verify(benefits).markClaimed(eq(31L), eq(61L), any());
    }

    @Test
    void crossUserAndNonProductAreBoundedWithoutCreatingSideEffects() {
        assertThatThrownBy(() -> service.claim(12, 31,
                new ClaimPhysicalBenefitCommand(requestId, 51L)))
                .isInstanceOfSatisfying(BusinessException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(ShippingErrorCode.CLAIM_NOT_ALLOWED));
        benefit.setPrizeType(PrizeType.COUPON);
        assertThatThrownBy(() -> service.claim(11, 31,
                new ClaimPhysicalBenefitCommand(requestId, 51L)))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(snapshots, orders);
    }

    @Test
    void deadlineEqualityIsExpired() {
        benefit.setClaimDeadline(LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        assertThatThrownBy(() -> service.claim(11, 31,
                new ClaimPhysicalBenefitCommand(requestId, 51L)))
                .isInstanceOfSatisfying(BusinessException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(ShippingErrorCode.CLAIM_EXPIRED));
        verifyNoInteractions(snapshots, orders);
    }

    @Test
    void malformedUuidIsRejectedBeforeLockingTheBenefit() {
        assertThatThrownBy(() -> service.claim(11, 31,
                new ClaimPhysicalBenefitCommand("not-a-uuid", 51L)))
                .isInstanceOfSatisfying(BusinessException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(ShippingErrorCode.SHIPPING_REQUEST_INVALID));
        verifyNoInteractions(benefits);
    }

    private UserBenefit benefit(LocalDateTime deadline) {
        UserBenefit row = new UserBenefit();
        row.setId(31L); row.setDrawRecordId(21L); row.setUserId(11L); row.setPrizeId(71L);
        row.setPrizeType(PrizeType.PHYSICAL); row.setRewardDefinitionId(81L);
        row.setRewardType(RewardType.PRODUCT); row.setRewardTargetId(91L); row.setRewardQuantity(2L);
        row.setRewardPayload("{\"skuId\":91,\"skuCode\":\"SKU-1\",\"productName\":\"礼盒\",\"skuName\":\"默认\",\"quantity\":2}");
        row.setRewardFingerprint("a".repeat(64)); row.setQuantity(2);
        row.setStatus(BenefitStatus.CLAIM_PENDING); row.setClaimDeadline(deadline);
        return row;
    }

    private LotteryDrawRecord draw(UserBenefit row) {
        LotteryDrawRecord draw = new LotteryDrawRecord();
        draw.setId(21L); draw.setUserId(row.getUserId()); draw.setActivityId(101L);
        draw.setPrizeId(row.getPrizeId()); draw.setPrizeType(row.getPrizeType());
        draw.setPrizeImageUrl("https://example.test/prize.png");
        draw.setRewardDefinitionId(row.getRewardDefinitionId()); draw.setRewardType(row.getRewardType());
        draw.setRewardTargetId(row.getRewardTargetId()); draw.setRewardQuantity(row.getRewardQuantity());
        draw.setRewardPayload(row.getRewardPayload()); draw.setRewardFingerprint(row.getRewardFingerprint());
        return draw;
    }

    private ShippingOrderView view() {
        return new ShippingOrderView(61L, "SHIPPING-61", ShippingSourceType.LOTTERY_BENEFIT,
                "31", 11L, 41L, "SKU-1", "礼盒", null, 2, "LOGISTICS-61",
                null, null, null, ShippingStatus.FULFILLING, null, null,
                null, null, null, null, LocalDateTime.now(), LocalDateTime.now());
    }
}
