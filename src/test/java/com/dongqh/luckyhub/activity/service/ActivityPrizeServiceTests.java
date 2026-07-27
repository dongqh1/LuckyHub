package com.dongqh.luckyhub.activity.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.dongqh.luckyhub.activity.dto.AddActivityPrizeCommand;
import com.dongqh.luckyhub.activity.dto.UpdateActivityPrizeCommand;
import com.dongqh.luckyhub.activity.entity.MarketingActivity;
import com.dongqh.luckyhub.activity.entity.MarketingActivityPrize;
import com.dongqh.luckyhub.activity.enums.ActivityErrorCode;
import com.dongqh.luckyhub.activity.enums.ActivityStatus;
import com.dongqh.luckyhub.activity.mapper.MarketingActivityMapper;
import com.dongqh.luckyhub.activity.mapper.MarketingActivityPrizeMapper;
import com.dongqh.luckyhub.activity.service.impl.ActivityPrizeServiceImpl;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.prize.entity.MarketingPrize;
import com.dongqh.luckyhub.prize.enums.PrizeLevel;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import com.dongqh.luckyhub.prize.mapper.MarketingPrizeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityPrizeServiceTests {

    private MarketingActivityMapper activityMapper;
    private MarketingActivityPrizeMapper relationMapper;
    private MarketingPrizeMapper prizeMapper;
    private ActivityPrizeService service;

    @BeforeEach
    void setUp() {
        activityMapper = mock(MarketingActivityMapper.class);
        relationMapper = mock(MarketingActivityPrizeMapper.class);
        prizeMapper = mock(MarketingPrizeMapper.class);
        service = new ActivityPrizeServiceImpl(activityMapper, relationMapper, prizeMapper);
    }

    @Test
    void addsEnabledPrizeAndInitializesRemainingStock() {
        when(activityMapper.selectById(5L)).thenReturn(activity(ActivityStatus.DRAFT));
        when(prizeMapper.selectById(7L)).thenReturn(prize(1));
        when(relationMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        service.add(5L, new AddActivityPrizeCommand(7L, 20, 100, 1));

        ArgumentCaptor<MarketingActivityPrize> captor =
                ArgumentCaptor.forClass(MarketingActivityPrize.class);
        verify(relationMapper).insert(captor.capture());
        assertThat(captor.getValue().getRemainingStock()).isEqualTo(100);
        assertThat(captor.getValue().getActivityId()).isEqualTo(5L);
    }

    @Test
    void rejectsDisabledPrizeAndLockedActivity() {
        when(activityMapper.selectById(5L)).thenReturn(activity(ActivityStatus.DRAFT));
        when(prizeMapper.selectById(7L)).thenReturn(prize(0));
        assertBusinessError(
                () -> service.add(5L, new AddActivityPrizeCommand(7L, 20, 100, 1)),
                ActivityErrorCode.ACTIVITY_HAS_DISABLED_PRIZE
        );

        when(activityMapper.selectById(5L)).thenReturn(activity(ActivityStatus.DISABLED));
        assertBusinessError(
                () -> service.add(5L, new AddActivityPrizeCommand(7L, 20, 100, 1)),
                ActivityErrorCode.ACTIVITY_STATE_CONFLICT
        );
    }

    @Test
    void rejectsDuplicateRelation() {
        when(activityMapper.selectById(5L)).thenReturn(activity(ActivityStatus.DRAFT));
        when(prizeMapper.selectById(7L)).thenReturn(prize(1));
        when(relationMapper.selectOne(any(Wrapper.class))).thenReturn(relation(100, 100));

        assertBusinessError(
                () -> service.add(5L, new AddActivityPrizeCommand(7L, 20, 100, 1)),
                ActivityErrorCode.ACTIVITY_PRIZE_DUPLICATE
        );
    }

    @Test
    void updatesStockWithoutRestoringConsumedQuantity() {
        when(activityMapper.selectById(5L)).thenReturn(activity(ActivityStatus.RUNNING));
        when(prizeMapper.selectById(7L)).thenReturn(prize(1));
        MarketingActivityPrize relation = relation(100, 80);
        when(relationMapper.selectOne(any(Wrapper.class))).thenReturn(relation);

        service.update(5L, 7L, new UpdateActivityPrizeCommand(30, 150, 2));

        assertThat(relation.getRemainingStock()).isEqualTo(130);
        assertThat(relation.getWeight()).isEqualTo(30);
        verify(relationMapper).updateById(relation);
    }

    @Test
    void rejectsTotalStockBelowConsumedQuantity() {
        when(activityMapper.selectById(5L)).thenReturn(activity(ActivityStatus.DRAFT));
        when(prizeMapper.selectById(7L)).thenReturn(prize(1));
        when(relationMapper.selectOne(any(Wrapper.class))).thenReturn(relation(100, 20));

        assertBusinessError(
                () -> service.update(5L, 7L, new UpdateActivityPrizeCommand(30, 70, 2)),
                ActivityErrorCode.ACTIVITY_STOCK_BELOW_CONSUMED
        );
        verify(relationMapper, never()).updateById(any(MarketingActivityPrize.class));
    }

    @Test
    void listsPrizeDetailsAndRemovesOnlyRelation() {
        when(activityMapper.selectById(5L)).thenReturn(activity(ActivityStatus.DRAFT));
        MarketingActivityPrize relation = relation(100, 80);
        when(relationMapper.selectList(any(Wrapper.class))).thenReturn(List.of(relation));
        when(prizeMapper.selectBatchIds(List.of(7L))).thenReturn(List.of(prize(1)));
        when(relationMapper.selectOne(any(Wrapper.class))).thenReturn(relation);

        assertThat(service.list(5L)).singleElement()
                .satisfies(view -> assertThat(view.prizeName()).isEqualTo("咖啡券"));

        service.remove(5L, 7L);
        verify(relationMapper).deleteById(relation.getId());
        verify(prizeMapper, never()).deleteById(any(Long.class));
    }

    private MarketingActivity activity(ActivityStatus status) {
        MarketingActivity activity = new MarketingActivity();
        activity.setId(5L);
        activity.setStatus(status);
        return activity;
    }

    private MarketingPrize prize(int status) {
        MarketingPrize prize = new MarketingPrize();
        prize.setId(7L);
        prize.setPrizeName("咖啡券");
        prize.setPrizeType(PrizeType.COUPON);
        prize.setPrizeLevel(PrizeLevel.FIRST);
        prize.setImageUrl("https://cdn.example/prize.png");
        prize.setStatus(status);
        return prize;
    }

    private MarketingActivityPrize relation(int total, int remaining) {
        MarketingActivityPrize relation = new MarketingActivityPrize();
        relation.setId(11L);
        relation.setActivityId(5L);
        relation.setPrizeId(7L);
        relation.setWeight(20);
        relation.setTotalStock(total);
        relation.setRemainingStock(remaining);
        relation.setSortOrder(1);
        return relation;
    }

    private void assertBusinessError(Runnable action, ActivityErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }
}
