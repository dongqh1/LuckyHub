package com.dongqh.luckyhub.reward.service;

import com.dongqh.luckyhub.reward.dto.CreateRewardDefinitionCommand;
import com.dongqh.luckyhub.reward.vo.RewardDefinitionView;

public interface RewardDefinitionService {
    RewardDefinitionView create(CreateRewardDefinitionCommand command);

    RewardDefinitionView get(long id);
}
