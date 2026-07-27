package com.dongqh.luckyhub.activity.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.dongqh.luckyhub.activity.dto.CreateActivityCommand;
import com.dongqh.luckyhub.activity.dto.UpdateActivityCommand;
import com.dongqh.luckyhub.activity.entity.MarketingActivity;
import com.dongqh.luckyhub.activity.entity.MarketingActivityPrize;
import com.dongqh.luckyhub.activity.enums.ActivityErrorCode;
import com.dongqh.luckyhub.activity.enums.ActivityStatus;
import com.dongqh.luckyhub.activity.mapper.MarketingActivityMapper;
import com.dongqh.luckyhub.activity.mapper.MarketingActivityPrizeMapper;
import com.dongqh.luckyhub.activity.service.impl.ActivityServiceImpl;
import com.dongqh.luckyhub.auth.context.LoginContext;
import com.dongqh.luckyhub.auth.model.LoginPrincipal;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.prize.entity.MarketingPrize;
import com.dongqh.luckyhub.prize.mapper.MarketingPrizeMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityServiceTests {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 1, 9, 0);

    private MarketingActivityMapper activityMapper;
    private MarketingActivityPrizeMapper relationMapper;
    private MarketingPrizeMapper prizeMapper;
    private ActivityService service;

    @BeforeEach
    void setUp() {
        activityMapper = mock(MarketingActivityMapper.class);
        relationMapper = mock(MarketingActivityPrizeMapper.class);
        prizeMapper = mock(MarketingPrizeMapper.class);
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        service = new ActivityServiceImpl(activityMapper, relationMapper, prizeMapper, clock);
        LoginContext.set(new LoginPrincipal(9L, "admin", "session"));
    }

    @AfterEach
    void tearDown() {
        LoginContext.clear();
    }

    @Test
    void createsTrimmedDraftForCurrentUser() {
        CreateActivityCommand command = new CreateActivityCommand(
                "  八月抽奖  ", "  会员活动  ",
                NOW.plusDays(1), NOW.plusDays(10), 3
        );

        service.create(command);

        ArgumentCaptor<MarketingActivity> captor = ArgumentCaptor.forClass(MarketingActivity.class);
        verify(activityMapper).insert(captor.capture());
        MarketingActivity saved = captor.getValue();
        assertThat(saved.getActivityName()).isEqualTo("八月抽奖");
        assertThat(saved.getDescription()).isEqualTo("会员活动");
        assertThat(saved.getStatus()).isEqualTo(ActivityStatus.DRAFT);
        assertThat(saved.getCreatedBy()).isEqualTo(9L);
    }

    @Test
    void rejectsInvalidTimeRangeBeforeInsert() {
        CreateActivityCommand command = new CreateActivityCommand(
                "活动", null, NOW.plusDays(1), NOW.plusDays(1), 1
        );

        assertBusinessError(() -> service.create(command), ActivityErrorCode.ACTIVITY_TIME_INVALID);
        verify(activityMapper, never()).insert(any(MarketingActivity.class));
    }

    @Test
    void publishesDraftAsScheduledWhenStartIsInFuture() {
        MarketingActivity activity = activity(ActivityStatus.DRAFT, NOW.plusHours(1), NOW.plusDays(1));
        when(activityMapper.selectById(1L)).thenReturn(activity);
        stubPublishablePrize();

        assertThat(service.publish(1L).status()).isEqualTo(ActivityStatus.SCHEDULED);
        assertThat(activity.getStatus()).isEqualTo(ActivityStatus.SCHEDULED);
        verify(activityMapper).updateById(activity);
    }

    @Test
    void publishesDraftAsRunningWhenStartHasArrived() {
        MarketingActivity activity = activity(ActivityStatus.DRAFT, NOW.minusHours(1), NOW.plusDays(1));
        when(activityMapper.selectById(1L)).thenReturn(activity);
        stubPublishablePrize();

        assertThat(service.publish(1L).status()).isEqualTo(ActivityStatus.RUNNING);
    }

    @Test
    void publishRejectsActivityWithoutPrizes() {
        MarketingActivity activity = activity(ActivityStatus.DRAFT, NOW.plusHours(1), NOW.plusDays(1));
        when(activityMapper.selectById(1L)).thenReturn(activity);
        when(relationMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        assertBusinessError(() -> service.publish(1L), ActivityErrorCode.ACTIVITY_HAS_NO_PRIZE);
        verify(activityMapper, never()).updateById(any(MarketingActivity.class));
    }

    @Test
    void endedAndDisabledActivitiesCannotBeUpdated() {
        UpdateActivityCommand command = new UpdateActivityCommand(
                "新名称", null, NOW.plusDays(1), NOW.plusDays(2), 2
        );
        MarketingActivity ended = activity(ActivityStatus.ENDED, NOW.minusDays(2), NOW.minusDays(1));
        when(activityMapper.selectById(1L)).thenReturn(ended);
        assertBusinessError(() -> service.update(1L, command), ActivityErrorCode.ACTIVITY_STATE_CONFLICT);

        MarketingActivity disabled = activity(ActivityStatus.DISABLED, NOW.plusDays(1), NOW.plusDays(2));
        when(activityMapper.selectById(1L)).thenReturn(disabled);
        assertBusinessError(() -> service.update(1L, command), ActivityErrorCode.ACTIVITY_STATE_CONFLICT);
    }

    @Test
    void disableIsIdempotentAndRestoreReturnsToDraft() {
        MarketingActivity activity = activity(ActivityStatus.RUNNING, NOW.minusHours(1), NOW.plusHours(1));
        when(activityMapper.selectById(1L)).thenReturn(activity);

        service.disable(1L);
        assertThat(activity.getStatus()).isEqualTo(ActivityStatus.DISABLED);
        verify(activityMapper).updateById(activity);

        assertThat(service.restore(1L).status()).isEqualTo(ActivityStatus.DRAFT);
        assertThat(activity.getStatus()).isEqualTo(ActivityStatus.DRAFT);
    }

    private void stubPublishablePrize() {
        MarketingActivityPrize relation = new MarketingActivityPrize();
        relation.setPrizeId(7L);
        relation.setWeight(10);
        relation.setTotalStock(100);
        relation.setRemainingStock(100);
        MarketingPrize prize = new MarketingPrize();
        prize.setId(7L);
        prize.setStatus(1);
        when(relationMapper.selectList(any(Wrapper.class))).thenReturn(List.of(relation));
        when(prizeMapper.selectBatchIds(List.of(7L))).thenReturn(List.of(prize));
    }

    private MarketingActivity activity(
            ActivityStatus status,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        MarketingActivity activity = new MarketingActivity();
        activity.setId(1L);
        activity.setActivityName("活动");
        activity.setStatus(status);
        activity.setStartTime(startTime);
        activity.setEndTime(endTime);
        activity.setDailyLimit(1);
        activity.setCreatedBy(9L);
        return activity;
    }

    private void assertBusinessError(Runnable action, ActivityErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }
}
