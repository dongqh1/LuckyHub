package com.dongqh.luckyhub.activity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dongqh.luckyhub.activity.dto.ActivityQuery;
import com.dongqh.luckyhub.activity.dto.CreateActivityCommand;
import com.dongqh.luckyhub.activity.dto.UpdateActivityCommand;
import com.dongqh.luckyhub.activity.entity.MarketingActivity;
import com.dongqh.luckyhub.activity.entity.MarketingActivityPrize;
import com.dongqh.luckyhub.activity.enums.ActivityErrorCode;
import com.dongqh.luckyhub.activity.enums.ActivityStatus;
import com.dongqh.luckyhub.activity.mapper.MarketingActivityMapper;
import com.dongqh.luckyhub.activity.mapper.MarketingActivityPrizeMapper;
import com.dongqh.luckyhub.activity.service.ActivityService;
import com.dongqh.luckyhub.activity.vo.ActivityView;
import com.dongqh.luckyhub.auth.context.LoginContext;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.prize.entity.MarketingPrize;
import com.dongqh.luckyhub.prize.mapper.MarketingPrizeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ActivityServiceImpl implements ActivityService {

    private static final int PRIZE_ENABLED = 1;

    private final MarketingActivityMapper activityMapper;
    private final MarketingActivityPrizeMapper relationMapper;
    private final MarketingPrizeMapper prizeMapper;
    private final Clock clock;

    @Autowired
    public ActivityServiceImpl(
            MarketingActivityMapper activityMapper,
            MarketingActivityPrizeMapper relationMapper,
            MarketingPrizeMapper prizeMapper
    ) {
        this(activityMapper, relationMapper, prizeMapper, Clock.systemDefaultZone());
    }

    public ActivityServiceImpl(
            MarketingActivityMapper activityMapper,
            MarketingActivityPrizeMapper relationMapper,
            MarketingPrizeMapper prizeMapper,
            Clock clock
    ) {
        this.activityMapper = activityMapper;
        this.relationMapper = relationMapper;
        this.prizeMapper = prizeMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ActivityView create(CreateActivityCommand command) {
        validateTimeRange(command.startTime(), command.endTime());
        MarketingActivity activity = new MarketingActivity();
        apply(activity, command.activityName(), command.description(),
                command.startTime(), command.endTime(), command.dailyLimit());
        activity.setStatus(ActivityStatus.DRAFT);
        activity.setCreatedBy(LoginContext.require().userId());
        activityMapper.insert(activity);
        return toView(activity);
    }

    @Override
    public ActivityView getById(long id) {
        return toView(requireActivity(id));
    }

    @Override
    public PageResponse<ActivityView> page(ActivityQuery query) {
        String name = normalize(query.getName());
        LambdaQueryWrapper<MarketingActivity> wrapper = new LambdaQueryWrapper<MarketingActivity>()
                .like(name != null, MarketingActivity::getActivityName, name)
                .eq(query.getStatus() != null, MarketingActivity::getStatus, query.getStatus())
                .orderByDesc(MarketingActivity::getCreatedAt)
                .orderByDesc(MarketingActivity::getId);
        Page<MarketingActivity> result = activityMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper
        );
        List<ActivityView> records = result.getRecords().stream().map(this::toView).toList();
        return new PageResponse<>(
                records, result.getTotal(), result.getCurrent(), result.getSize(), result.getPages()
        );
    }

    @Override
    @Transactional
    public ActivityView update(long id, UpdateActivityCommand command) {
        MarketingActivity activity = requireActivity(id);
        if (activity.getStatus() == ActivityStatus.DISABLED
                || activity.getStatus() == ActivityStatus.ENDED) {
            throw new BusinessException(ActivityErrorCode.ACTIVITY_STATE_CONFLICT);
        }
        validateTimeRange(command.startTime(), command.endTime());
        if (activity.getStatus() != ActivityStatus.DRAFT
                && !command.endTime().isAfter(now())) {
            throw new BusinessException(ActivityErrorCode.ACTIVITY_TIME_INVALID);
        }
        apply(activity, command.activityName(), command.description(),
                command.startTime(), command.endTime(), command.dailyLimit());
        if (activity.getStatus() != ActivityStatus.DRAFT) {
            activity.setStatus(resolvePublishedStatus(command.startTime()));
        }
        activityMapper.updateById(activity);
        return toView(activity);
    }

    @Override
    @Transactional
    public ActivityView publish(long id) {
        MarketingActivity activity = requireActivity(id);
        if (activity.getStatus() != ActivityStatus.DRAFT) {
            throw new BusinessException(ActivityErrorCode.ACTIVITY_STATE_CONFLICT);
        }
        validateTimeRange(activity.getStartTime(), activity.getEndTime());
        if (!activity.getEndTime().isAfter(now())) {
            throw new BusinessException(ActivityErrorCode.ACTIVITY_TIME_INVALID);
        }
        validatePrizeConfiguration(id);
        activity.setStatus(resolvePublishedStatus(activity.getStartTime()));
        activityMapper.updateById(activity);
        return toView(activity);
    }

    @Override
    @Transactional
    public void disable(long id) {
        MarketingActivity activity = requireActivity(id);
        if (activity.getStatus() == ActivityStatus.DISABLED) {
            return;
        }
        activity.setStatus(ActivityStatus.DISABLED);
        activityMapper.updateById(activity);
    }

    @Override
    @Transactional
    public ActivityView restore(long id) {
        MarketingActivity activity = requireActivity(id);
        if (activity.getStatus() != ActivityStatus.DISABLED) {
            throw new BusinessException(ActivityErrorCode.ACTIVITY_STATE_CONFLICT);
        }
        activity.setStatus(ActivityStatus.DRAFT);
        activityMapper.updateById(activity);
        return toView(activity);
    }

    private void validatePrizeConfiguration(long activityId) {
        List<MarketingActivityPrize> relations = relationMapper.selectList(
                new LambdaQueryWrapper<MarketingActivityPrize>()
                        .eq(MarketingActivityPrize::getActivityId, activityId)
        );
        if (relations.isEmpty()) {
            throw new BusinessException(ActivityErrorCode.ACTIVITY_HAS_NO_PRIZE);
        }
        List<Long> prizeIds = relations.stream().map(MarketingActivityPrize::getPrizeId).toList();
        Map<Long, MarketingPrize> prizes = prizeMapper.selectBatchIds(prizeIds).stream()
                .collect(Collectors.toMap(MarketingPrize::getId, Function.identity()));
        boolean disabledPrize = relations.stream().anyMatch(relation -> {
            MarketingPrize prize = prizes.get(relation.getPrizeId());
            return prize == null || !Integer.valueOf(PRIZE_ENABLED).equals(prize.getStatus());
        });
        if (disabledPrize) {
            throw new BusinessException(ActivityErrorCode.ACTIVITY_HAS_DISABLED_PRIZE);
        }
        boolean invalid = relations.stream().anyMatch(relation ->
                relation.getWeight() == null || relation.getWeight() <= 0
                        || relation.getTotalStock() == null || relation.getTotalStock() < 0
                        || relation.getRemainingStock() == null || relation.getRemainingStock() < 0
                        || relation.getRemainingStock() > relation.getTotalStock()
        );
        if (invalid) {
            throw new BusinessException(ActivityErrorCode.ACTIVITY_STATE_CONFLICT);
        }
        if (relations.stream().noneMatch(relation -> relation.getRemainingStock() > 0)) {
            throw new BusinessException(ActivityErrorCode.ACTIVITY_HAS_NO_AVAILABLE_STOCK);
        }
    }

    private MarketingActivity requireActivity(long id) {
        MarketingActivity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new BusinessException(ActivityErrorCode.ACTIVITY_NOT_FOUND);
        }
        return activity;
    }

    private void validateTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new BusinessException(ActivityErrorCode.ACTIVITY_TIME_INVALID);
        }
    }

    private ActivityStatus resolvePublishedStatus(LocalDateTime startTime) {
        return now().isBefore(startTime) ? ActivityStatus.SCHEDULED : ActivityStatus.RUNNING;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private void apply(
            MarketingActivity activity,
            String activityName,
            String description,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Integer dailyLimit
    ) {
        activity.setActivityName(activityName.trim());
        activity.setDescription(normalize(description));
        activity.setStartTime(startTime);
        activity.setEndTime(endTime);
        activity.setDailyLimit(dailyLimit);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private ActivityView toView(MarketingActivity activity) {
        return new ActivityView(
                activity.getId(),
                activity.getActivityName(),
                activity.getDescription(),
                activity.getStatus(),
                activity.getStartTime(),
                activity.getEndTime(),
                activity.getDailyLimit(),
                activity.getCreatedBy(),
                activity.getCreatedAt(),
                activity.getUpdatedAt()
        );
    }
}
