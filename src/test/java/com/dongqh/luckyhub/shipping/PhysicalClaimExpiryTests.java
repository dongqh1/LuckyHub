package com.dongqh.luckyhub.shipping;

import com.dongqh.luckyhub.activity.entity.MarketingActivityPrize;
import com.dongqh.luckyhub.activity.mapper.MarketingActivityPrizeMapper;
import com.dongqh.luckyhub.benefit.entity.UserBenefit;
import com.dongqh.luckyhub.benefit.enums.BenefitStatus;
import com.dongqh.luckyhub.benefit.mapper.UserBenefitMapper;
import com.dongqh.luckyhub.inventory.service.ActivityPrizeInventoryService;
import com.dongqh.luckyhub.lottery.entity.LotteryDrawRecord;
import com.dongqh.luckyhub.lottery.mapper.LotteryDrawRecordMapper;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import com.dongqh.luckyhub.reward.enums.RewardType;
import com.dongqh.luckyhub.shipping.service.impl.PhysicalClaimExpiryServiceImpl;
import com.dongqh.luckyhub.shipping.service.PhysicalClaimExpiryWorker;
import com.dongqh.luckyhub.shipping.service.impl.PhysicalClaimExpiryWorkerImpl;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PhysicalClaimExpiryTests {
    UserBenefitMapper benefits = mock(UserBenefitMapper.class);
    LotteryDrawRecordMapper draws = mock(LotteryDrawRecordMapper.class);
    MarketingActivityPrizeMapper activityPrizes = mock(MarketingActivityPrizeMapper.class);
    ActivityPrizeInventoryService inventory = mock(ActivityPrizeInventoryService.class);
    PhysicalClaimExpiryWorker worker = mock(PhysicalClaimExpiryWorker.class);
    PhysicalClaimExpiryServiceImpl service = new PhysicalClaimExpiryServiceImpl(benefits, worker);

    @Test
    void expiredBenefitReturnsResolvedActivityPrizeInventoryExactlyOnce() {
        LocalDateTime now = LocalDateTime.now();
        UserBenefit benefit = benefit(now.minusSeconds(1));
        LotteryDrawRecord draw = draw(benefit);
        MarketingActivityPrize relation = new MarketingActivityPrize();
        relation.setId(301L);
        PhysicalClaimExpiryWorkerImpl transactionWorker = new PhysicalClaimExpiryWorkerImpl(
                benefits, draws, activityPrizes, inventory,
                JsonMapper.builder().findAndAddModules().build());
        when(benefits.selectByIdForUpdate(31)).thenReturn(benefit);
        when(draws.selectById(21L)).thenReturn(draw);
        when(activityPrizes.lockByActivityAndPrize(101, 71)).thenReturn(relation);
        when(benefits.markClaimExpired(31, now)).thenReturn(1);

        assertThat(transactionWorker.expireOne(31, now)).isTrue();
        verify(inventory).returnExpiredClaim(301, 91, "CLAIM-EXPIRE-31");
        verify(benefits).markClaimExpired(31, now);
    }

    @Test
    void compensationFailureDoesNotAdvanceBenefitState() {
        LocalDateTime now = LocalDateTime.now();
        UserBenefit benefit = benefit(now.minusSeconds(1));
        MarketingActivityPrize relation = new MarketingActivityPrize(); relation.setId(301L);
        PhysicalClaimExpiryWorkerImpl transactionWorker = new PhysicalClaimExpiryWorkerImpl(
                benefits, draws, activityPrizes, inventory,
                JsonMapper.builder().findAndAddModules().build());
        when(benefits.selectByIdForUpdate(31)).thenReturn(benefit);
        when(draws.selectById(21L)).thenReturn(draw(benefit));
        when(activityPrizes.lockByActivityAndPrize(101, 71)).thenReturn(relation);
        doThrow(new IllegalStateException("stock failure")).when(inventory)
                .returnExpiredClaim(301, 91, "CLAIM-EXPIRE-31");

        assertThatThrownBy(() -> transactionWorker.expireOne(31, now)).isInstanceOf(IllegalStateException.class);
        verify(benefits, never()).markClaimExpired(anyLong(), any());
    }

    @Test
    void poisonCandidateDoesNotStopOrStarveLaterDueBenefit() {
        LocalDateTime now = LocalDateTime.now();
        when(benefits.selectDueClaimIdsAfter(now, 0L, 50)).thenReturn(List.of(31L, 32L));
        doThrow(new IllegalStateException("poison")).when(worker).expireOne(31L, now);
        when(worker.expireOne(32L, now)).thenReturn(true);

        assertThat(service.expireDue(1, now)).isOne();
        verify(worker).expireOne(31L, now);
        verify(worker).expireOne(32L, now);
    }

    @Test
    void eachCandidateWorkerRequiresANewTransaction() throws Exception {
        Transactional transaction = PhysicalClaimExpiryWorkerImpl.class
                .getMethod("expireOne", long.class, LocalDateTime.class)
                .getAnnotation(Transactional.class);
        assertThat(transaction).isNotNull();
        assertThat(transaction.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    void aFullPageOfPoisonCandidatesCannotStarveTheNextPage() {
        LocalDateTime now = LocalDateTime.now();
        List<Long> poisonIds = LongStream.rangeClosed(1, 50).boxed().toList();
        when(benefits.selectDueClaimIdsAfter(now, 0L, 50)).thenReturn(poisonIds);
        when(benefits.selectDueClaimIdsAfter(now, 50L, 50)).thenReturn(List.of(51L));
        when(worker.expireOne(anyLong(), eq(now))).thenAnswer(invocation -> {
            long id = invocation.getArgument(0);
            if (id <= 50) throw new IllegalStateException("poison");
            return true;
        });

        assertThat(service.expireDue(1, now)).isOne();
        verify(worker).expireOne(51L, now);
    }

    @Test
    void v17GivesMigratedPendingBenefitsSevenDayGraceWithoutChangingMigration() throws Exception {
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V17__add_shipping_and_physical_claim.sql"),
                StandardCharsets.UTF_8);
        assertThat(migration).contains("DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 7 DAY)")
                .contains("WHERE status = 'CLAIM_PENDING' AND claim_deadline IS NULL");
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
        draw.setId(21L); draw.setUserId(11L); draw.setActivityId(101L); draw.setPrizeId(71L);
        draw.setPrizeType(PrizeType.PHYSICAL); draw.setRewardDefinitionId(81L);
        draw.setRewardType(RewardType.PRODUCT); draw.setRewardTargetId(91L); draw.setRewardQuantity(2L);
        draw.setRewardPayload(row.getRewardPayload()); draw.setRewardFingerprint(row.getRewardFingerprint());
        return draw;
    }
}
