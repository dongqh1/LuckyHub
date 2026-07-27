package com.dongqh.luckyhub.activity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dongqh.luckyhub.activity.dto.AddActivityPrizeCommand;
import com.dongqh.luckyhub.activity.dto.UpdateActivityPrizeCommand;
import com.dongqh.luckyhub.activity.entity.MarketingActivity;
import com.dongqh.luckyhub.activity.entity.MarketingActivityPrize;
import com.dongqh.luckyhub.activity.enums.ActivityErrorCode;
import com.dongqh.luckyhub.activity.enums.ActivityStatus;
import com.dongqh.luckyhub.activity.mapper.MarketingActivityMapper;
import com.dongqh.luckyhub.activity.mapper.MarketingActivityPrizeMapper;
import com.dongqh.luckyhub.activity.service.ActivityPrizeService;
import com.dongqh.luckyhub.activity.vo.ActivityPrizeView;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.prize.entity.MarketingPrize;
import com.dongqh.luckyhub.prize.mapper.MarketingPrizeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ActivityPrizeServiceImpl implements ActivityPrizeService {

    private static final int PRIZE_ENABLED = 1;

    private final MarketingActivityMapper activityMapper;
    private final MarketingActivityPrizeMapper relationMapper;
    private final MarketingPrizeMapper prizeMapper;

    public ActivityPrizeServiceImpl(
            MarketingActivityMapper activityMapper,
            MarketingActivityPrizeMapper relationMapper,
            MarketingPrizeMapper prizeMapper
    ) {
        this.activityMapper = activityMapper;
        this.relationMapper = relationMapper;
        this.prizeMapper = prizeMapper;
    }

    @Override
    @Transactional
    public ActivityPrizeView add(long activityId, AddActivityPrizeCommand command) {
        requireConfigurableActivity(activityId);
        MarketingPrize prize = requireEnabledPrize(command.prizeId());
        if (findRelation(activityId, command.prizeId()) != null) {
            throw new BusinessException(ActivityErrorCode.ACTIVITY_PRIZE_DUPLICATE);
        }
        MarketingActivityPrize relation = new MarketingActivityPrize();
        relation.setActivityId(activityId);
        relation.setPrizeId(command.prizeId());
        relation.setWeight(command.weight());
        relation.setTotalStock(command.totalStock());
        relation.setRemainingStock(command.totalStock());
        relation.setSortOrder(command.sortOrder());
        relationMapper.insert(relation);
        return toView(relation, prize);
    }

    @Override
    public List<ActivityPrizeView> list(long activityId) {
        requireActivity(activityId);
        List<MarketingActivityPrize> relations = relationMapper.selectList(
                new LambdaQueryWrapper<MarketingActivityPrize>()
                        .eq(MarketingActivityPrize::getActivityId, activityId)
                        .orderByAsc(MarketingActivityPrize::getSortOrder)
                        .orderByAsc(MarketingActivityPrize::getId)
        );
        if (relations.isEmpty()) {
            return List.of();
        }
        List<Long> prizeIds = relations.stream().map(MarketingActivityPrize::getPrizeId).toList();
        Map<Long, MarketingPrize> prizes = prizeMapper.selectBatchIds(prizeIds).stream()
                .collect(Collectors.toMap(MarketingPrize::getId, Function.identity()));
        return relations.stream()
                .map(relation -> toView(relation, prizes.get(relation.getPrizeId())))
                .toList();
    }

    @Override
    @Transactional
    public ActivityPrizeView update(
            long activityId,
            long prizeId,
            UpdateActivityPrizeCommand command
    ) {
        requireConfigurableActivity(activityId);
        MarketingPrize prize = requireEnabledPrize(prizeId);
        MarketingActivityPrize relation = requireRelation(activityId, prizeId);
        int consumedStock = relation.getTotalStock() - relation.getRemainingStock();
        if (command.totalStock() < consumedStock) {
            throw new BusinessException(ActivityErrorCode.ACTIVITY_STOCK_BELOW_CONSUMED);
        }
        relation.setWeight(command.weight());
        relation.setTotalStock(command.totalStock());
        relation.setRemainingStock(command.totalStock() - consumedStock);
        relation.setSortOrder(command.sortOrder());
        relationMapper.updateById(relation);
        return toView(relation, prize);
    }

    @Override
    @Transactional
    public void remove(long activityId, long prizeId) {
        requireConfigurableActivity(activityId);
        MarketingActivityPrize relation = requireRelation(activityId, prizeId);
        relationMapper.deleteById(relation.getId());
    }

    private MarketingActivity requireConfigurableActivity(long activityId) {
        MarketingActivity activity = requireActivity(activityId);
        if (activity.getStatus() == ActivityStatus.DISABLED
                || activity.getStatus() == ActivityStatus.ENDED) {
            throw new BusinessException(ActivityErrorCode.ACTIVITY_STATE_CONFLICT);
        }
        return activity;
    }

    private MarketingActivity requireActivity(long activityId) {
        MarketingActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException(ActivityErrorCode.ACTIVITY_NOT_FOUND);
        }
        return activity;
    }

    private MarketingPrize requireEnabledPrize(long prizeId) {
        MarketingPrize prize = prizeMapper.selectById(prizeId);
        if (prize == null) {
            throw new BusinessException(ActivityErrorCode.ACTIVITY_PRIZE_NOT_FOUND);
        }
        if (!Integer.valueOf(PRIZE_ENABLED).equals(prize.getStatus())) {
            throw new BusinessException(ActivityErrorCode.ACTIVITY_HAS_DISABLED_PRIZE);
        }
        return prize;
    }

    private MarketingActivityPrize requireRelation(long activityId, long prizeId) {
        MarketingActivityPrize relation = findRelation(activityId, prizeId);
        if (relation == null) {
            throw new BusinessException(ActivityErrorCode.ACTIVITY_PRIZE_NOT_FOUND);
        }
        return relation;
    }

    private MarketingActivityPrize findRelation(long activityId, long prizeId) {
        return relationMapper.selectOne(
                new LambdaQueryWrapper<MarketingActivityPrize>()
                        .eq(MarketingActivityPrize::getActivityId, activityId)
                        .eq(MarketingActivityPrize::getPrizeId, prizeId)
        );
    }

    private ActivityPrizeView toView(
            MarketingActivityPrize relation,
            MarketingPrize prize
    ) {
        return new ActivityPrizeView(
                relation.getId(),
                relation.getActivityId(),
                relation.getPrizeId(),
                prize == null ? null : prize.getPrizeName(),
                prize == null ? null : prize.getPrizeType(),
                prize == null ? null : prize.getPrizeLevel(),
                prize == null ? null : prize.getImageUrl(),
                prize == null ? null : prize.getStatus(),
                relation.getWeight(),
                relation.getTotalStock(),
                relation.getRemainingStock(),
                relation.getSortOrder()
        );
    }
}
