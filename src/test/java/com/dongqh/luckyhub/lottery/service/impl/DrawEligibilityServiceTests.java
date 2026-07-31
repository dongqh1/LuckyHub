package com.dongqh.luckyhub.lottery.service.impl;

import com.dongqh.luckyhub.activity.entity.MarketingActivity;
import com.dongqh.luckyhub.activity.entity.MarketingActivityPrize;
import com.dongqh.luckyhub.activity.enums.ActivityStatus;
import com.dongqh.luckyhub.activity.mapper.MarketingActivityMapper;
import com.dongqh.luckyhub.activity.mapper.MarketingActivityPrizeMapper;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.lottery.enums.LotteryErrorCode;
import com.dongqh.luckyhub.lottery.service.DrawEligibilityService;
import com.dongqh.luckyhub.prize.entity.MarketingPrize;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import com.dongqh.luckyhub.prize.mapper.MarketingPrizeMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DrawEligibilityServiceTests {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-31T04:00:00Z"), SHANGHAI);
    private final MarketingActivityMapper activityMapper = mock(MarketingActivityMapper.class);
    private final MarketingActivityPrizeMapper relationMapper = mock(MarketingActivityPrizeMapper.class);
    private final MarketingPrizeMapper prizeMapper = mock(MarketingPrizeMapper.class);
    private final DrawEligibilityService service = new DrawEligibilityServiceImpl(
            activityMapper, relationMapper, prizeMapper, CLOCK);

    @Test
    void loadsOneImmutableShanghaiTimeConfigurationSnapshot() {
        MarketingActivity activity = runningActivity();
        MarketingActivityPrize relation = new MarketingActivityPrize();
        relation.setId(2L); relation.setActivityId(1L); relation.setPrizeId(3L);
        relation.setWeight(40); relation.setRemainingStock(5); relation.setSortOrder(1);
        MarketingPrize prize = new MarketingPrize();
        prize.setId(3L); prize.setPrizeName("券"); prize.setPrizeType(PrizeType.COUPON);
        prize.setImageUrl("https://img"); prize.setStatus(0);
        when(activityMapper.selectById(1L)).thenReturn(activity);
        when(relationMapper.selectList(any())).thenReturn(List.of(relation));
        when(prizeMapper.selectByIds(List.of(3L))).thenReturn(List.of(prize));

        DrawEligibilityService.EligibilitySnapshot snapshot = service.load(1L);

        assertThat(snapshot.dailyLimit()).isEqualTo(10);
        assertThat(snapshot.noWinWeight()).isEqualTo(60);
        assertThat(snapshot.snapshotTime().toString()).isEqualTo("2026-07-31T12:00");
        assertThat(snapshot.prizes()).singleElement().satisfies(item -> {
            assertThat(item.weight()).isEqualTo(40);
            assertThat(item.remainingStock()).isEqualTo(5);
            assertThat(item.enabled()).isFalse();
        });
        verify(activityMapper, times(1)).selectById(1L);
        assertThatThrownBy(() -> snapshot.prizes().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNonRunningOrOutsideTimeWindow() {
        MarketingActivity activity = runningActivity();
        activity.setStatus(ActivityStatus.DISABLED);
        when(activityMapper.selectById(1L)).thenReturn(activity);
        assertError(LotteryErrorCode.ACTIVITY_NOT_AVAILABLE);

        activity.setStatus(ActivityStatus.RUNNING);
        activity.setEndTime(java.time.LocalDateTime.of(2026, 7, 31, 12, 0));
        assertError(LotteryErrorCode.ACTIVITY_NOT_AVAILABLE);
        verifyNoInteractions(relationMapper, prizeMapper);
    }

    @Test
    void distinguishesMissingActivityAndInvalidEmptyWeightConfiguration() {
        when(activityMapper.selectById(1L)).thenReturn(null);
        assertError(LotteryErrorCode.ACTIVITY_NOT_FOUND);

        MarketingActivity activity = runningActivity();
        activity.setNoWinWeight(0);
        when(activityMapper.selectById(1L)).thenReturn(activity);
        when(relationMapper.selectList(any())).thenReturn(List.of());
        assertError(LotteryErrorCode.DRAW_WEIGHT_INVALID);
    }

    private MarketingActivity runningActivity() {
        MarketingActivity activity = new MarketingActivity();
        activity.setId(1L); activity.setStatus(ActivityStatus.RUNNING);
        activity.setStartTime(java.time.LocalDateTime.of(2026, 7, 31, 11, 0));
        activity.setEndTime(java.time.LocalDateTime.of(2026, 7, 31, 13, 0));
        activity.setDailyLimit(10); activity.setNoWinWeight(60);
        return activity;
    }

    private void assertError(LotteryErrorCode code) {
        assertThatThrownBy(() -> service.load(1L)).isInstanceOfSatisfying(
                BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(code));
    }
}
