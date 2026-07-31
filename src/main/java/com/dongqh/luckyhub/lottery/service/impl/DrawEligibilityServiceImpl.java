package com.dongqh.luckyhub.lottery.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dongqh.luckyhub.activity.entity.MarketingActivity;
import com.dongqh.luckyhub.activity.entity.MarketingActivityPrize;
import com.dongqh.luckyhub.activity.enums.ActivityStatus;
import com.dongqh.luckyhub.activity.mapper.MarketingActivityMapper;
import com.dongqh.luckyhub.activity.mapper.MarketingActivityPrizeMapper;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.lottery.config.LotteryProperties;
import com.dongqh.luckyhub.lottery.enums.LotteryErrorCode;
import com.dongqh.luckyhub.lottery.model.DrawPrizeSnapshot;
import com.dongqh.luckyhub.lottery.service.DrawEligibilityService;
import com.dongqh.luckyhub.prize.entity.MarketingPrize;
import com.dongqh.luckyhub.prize.mapper.MarketingPrizeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DrawEligibilityServiceImpl implements DrawEligibilityService {
    private final MarketingActivityMapper activityMapper;
    private final MarketingActivityPrizeMapper relationMapper;
    private final MarketingPrizeMapper prizeMapper;
    private final Clock clock;

    @Autowired
    public DrawEligibilityServiceImpl(MarketingActivityMapper activityMapper,
                                      MarketingActivityPrizeMapper relationMapper,
                                      MarketingPrizeMapper prizeMapper,
                                      LotteryProperties properties) {
        this(activityMapper, relationMapper, prizeMapper, Clock.system(properties.zoneId()));
    }

    DrawEligibilityServiceImpl(MarketingActivityMapper activityMapper,
                               MarketingActivityPrizeMapper relationMapper,
                               MarketingPrizeMapper prizeMapper, Clock clock) {
        this.activityMapper = activityMapper;
        this.relationMapper = relationMapper;
        this.prizeMapper = prizeMapper;
        this.clock = clock;
    }

    @Override
    public EligibilitySnapshot load(long activityId) {
        MarketingActivity activity = activityMapper.selectById(activityId);
        if (activity == null) throw new BusinessException(LotteryErrorCode.ACTIVITY_NOT_FOUND);
        // MySQL DATETIME(3) stores milliseconds. Normalize before the synchronous response so
        // an idempotent retry read from MySQL is byte-for-byte equivalent to the first response.
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
        if (activity.getStatus() != ActivityStatus.RUNNING
                || activity.getStartTime() == null || activity.getEndTime() == null
                || now.isBefore(activity.getStartTime()) || !now.isBefore(activity.getEndTime())) {
            throw new BusinessException(LotteryErrorCode.ACTIVITY_NOT_AVAILABLE);
        }
        List<MarketingActivityPrize> relations = relationMapper.selectList(
                new LambdaQueryWrapper<MarketingActivityPrize>()
                        .eq(MarketingActivityPrize::getActivityId, activityId)
                        .orderByAsc(MarketingActivityPrize::getSortOrder)
                        .orderByAsc(MarketingActivityPrize::getId));
        List<Long> prizeIds = relations.stream().map(MarketingActivityPrize::getPrizeId).distinct().toList();
        Map<Long, MarketingPrize> prizes = prizeIds.isEmpty() ? Map.of()
                : prizeMapper.selectByIds(prizeIds).stream()
                .collect(Collectors.toMap(MarketingPrize::getId, Function.identity()));
        List<DrawPrizeSnapshot> snapshots = relations.stream().map(relation -> {
            MarketingPrize prize = prizes.get(relation.getPrizeId());
            if (prize == null) throw new BusinessException(LotteryErrorCode.DRAW_WEIGHT_INVALID);
            return new DrawPrizeSnapshot(relation.getId(), prize.getId(), prize.getPrizeName(),
                    prize.getPrizeType(), prize.getImageUrl(), relation.getWeight(),
                    relation.getRemainingStock(), Integer.valueOf(1).equals(prize.getStatus()));
        }).toList();
        int dailyLimit = activity.getDailyLimit() == null ? 0 : activity.getDailyLimit();
        int noWinWeight = activity.getNoWinWeight() == null ? 0 : activity.getNoWinWeight();
        if (dailyLimit < 0 || noWinWeight < 0 || (snapshots.isEmpty() && noWinWeight == 0)) {
            throw new BusinessException(LotteryErrorCode.DRAW_WEIGHT_INVALID);
        }
        return new EligibilitySnapshot(activityId, dailyLimit, noWinWeight, snapshots, now);
    }
}
