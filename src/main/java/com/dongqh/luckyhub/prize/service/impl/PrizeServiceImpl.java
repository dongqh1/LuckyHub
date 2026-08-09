package com.dongqh.luckyhub.prize.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.prize.dto.CreatePrizeCommand;
import com.dongqh.luckyhub.prize.dto.PrizeQuery;
import com.dongqh.luckyhub.prize.dto.UpdatePrizeCommand;
import com.dongqh.luckyhub.prize.entity.MarketingPrize;
import com.dongqh.luckyhub.prize.enums.PrizeErrorCode;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import com.dongqh.luckyhub.prize.mapper.MarketingPrizeMapper;
import com.dongqh.luckyhub.prize.service.PrizeService;
import com.dongqh.luckyhub.prize.vo.PrizeView;
import com.dongqh.luckyhub.reward.entity.RewardDefinition;
import com.dongqh.luckyhub.reward.mapper.RewardDefinitionMapper;
import com.dongqh.luckyhub.reward.support.RewardPrizeTypeMapping;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class PrizeServiceImpl implements PrizeService {

    private static final int ENABLED = 1;
    private static final int DISABLED = 0;

    private final MarketingPrizeMapper mapper;
    private final RewardDefinitionMapper rewards;

    public PrizeServiceImpl(MarketingPrizeMapper mapper, RewardDefinitionMapper rewards) {
        this.mapper = mapper;
        this.rewards = rewards;
    }

    @Override
    @Transactional
    public PrizeView create(CreatePrizeCommand command) {
        MarketingPrize prize = new MarketingPrize();
        apply(prize, command.prizeName(), command.prizeType(), command.prizeLevel(),
                command.imageUrl(), command.description(), command.stackable());
        prize.setRewardDefinitionId(requireCompatibleReward(
                command.rewardDefinitionId(), command.prizeType()).getId());
        prize.setStatus(ENABLED);
        mapper.insert(prize);
        return toView(prize);
    }

    @Override
    public PrizeView getById(long id) {
        return toView(requirePrize(id));
    }

    @Override
    public PageResponse<PrizeView> page(PrizeQuery query) {
        String name = normalize(query.getName());
        LambdaQueryWrapper<MarketingPrize> wrapper = new LambdaQueryWrapper<MarketingPrize>()
                .like(name != null, MarketingPrize::getPrizeName, name)
                .eq(query.getType() != null, MarketingPrize::getPrizeType, query.getType())
                .eq(query.getStatus() != null, MarketingPrize::getStatus, query.getStatus())
                .orderByDesc(MarketingPrize::getCreatedAt)
                .orderByDesc(MarketingPrize::getId);
        Page<MarketingPrize> result = mapper.selectPage(new Page<>(query.getPage(), query.getSize()), wrapper);
        List<PrizeView> records = result.getRecords().stream().map(this::toView).toList();
        return new PageResponse<>(
                records,
                result.getTotal(),
                result.getCurrent(),
                result.getSize(),
                result.getPages()
        );
    }

    @Override
    @Transactional
    public PrizeView update(long id, UpdatePrizeCommand command) {
        MarketingPrize prize = requirePrize(id);
        apply(prize, command.prizeName(), command.prizeType(), command.prizeLevel(),
                command.imageUrl(), command.description(), command.stackable());
        if (command.rewardDefinitionId() != null) {
            if (prize.getRewardDefinitionId() != null
                    && !prize.getRewardDefinitionId().equals(command.rewardDefinitionId())) {
                throw new BusinessException(PrizeErrorCode.REWARD_BINDING_INVALID);
            }
            prize.setRewardDefinitionId(requireCompatibleReward(
                    command.rewardDefinitionId(), command.prizeType()).getId());
        } else if (prize.getRewardDefinitionId() != null) {
            requireCompatibleReward(prize.getRewardDefinitionId(), command.prizeType());
        }
        mapper.updateById(prize);
        return toView(prize);
    }

    @Override
    @Transactional
    public void disable(long id) {
        MarketingPrize prize = requirePrize(id);
        if (Integer.valueOf(DISABLED).equals(prize.getStatus())) {
            return;
        }
        prize.setStatus(DISABLED);
        mapper.updateById(prize);
    }

    private MarketingPrize requirePrize(long id) {
        MarketingPrize prize = mapper.selectById(id);
        if (prize == null) {
            throw new BusinessException(PrizeErrorCode.PRIZE_NOT_FOUND);
        }
        return prize;
    }

    private void apply(
            MarketingPrize prize,
            String prizeName,
            com.dongqh.luckyhub.prize.enums.PrizeType prizeType,
            com.dongqh.luckyhub.prize.enums.PrizeLevel prizeLevel,
            String imageUrl,
            String description,
            Boolean stackable
    ) {
        prize.setPrizeName(prizeName.trim());
        prize.setPrizeType(prizeType);
        prize.setPrizeLevel(prizeLevel);
        prize.setImageUrl(normalize(imageUrl));
        prize.setDescription(normalize(description));
        prize.setStackable(stackable);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private RewardDefinition requireCompatibleReward(Long rewardId, PrizeType prizeType) {
        if (rewardId == null) throw new BusinessException(PrizeErrorCode.REWARD_BINDING_INVALID);
        RewardDefinition reward = rewards.selectById(rewardId);
        if (reward == null || !Integer.valueOf(ENABLED).equals(reward.getStatus())
                || !RewardPrizeTypeMapping.matches(reward.getRewardType(), prizeType)) {
            throw new BusinessException(PrizeErrorCode.REWARD_BINDING_INVALID);
        }
        return reward;
    }

    private PrizeView toView(MarketingPrize prize) {
        RewardDefinition reward = prize.getRewardDefinitionId() == null
                ? null : rewards.selectById(prize.getRewardDefinitionId());
        return new PrizeView(
                prize.getId(),
                prize.getPrizeName(),
                prize.getPrizeType(),
                prize.getPrizeLevel(),
                prize.getImageUrl(),
                prize.getDescription(),
                prize.getStackable(),
                prize.getStatus(),
                prize.getCreatedAt(),
                prize.getUpdatedAt(),
                prize.getRewardDefinitionId(),
                reward == null ? null : reward.getRewardType()
        );
    }
}
