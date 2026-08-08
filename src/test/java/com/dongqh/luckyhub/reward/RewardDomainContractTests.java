package com.dongqh.luckyhub.reward;

import com.dongqh.luckyhub.reward.entity.RewardDefinition;
import com.dongqh.luckyhub.reward.enums.RewardType;
import com.dongqh.luckyhub.reward.mapper.RewardDefinitionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RewardDomainContractTests {

    @Autowired RewardDefinitionMapper mapper;

    private Long rewardId;

    @AfterEach
    void cleanUp() {
        if (rewardId != null) {
            mapper.deleteById(rewardId);
        }
    }

    @Test
    void exposesStableRewardTypes() {
        assertThat(RewardType.values()).containsExactly(
                RewardType.PRODUCT,
                RewardType.COUPON,
                RewardType.POINTS,
                RewardType.MEMBERSHIP,
                RewardType.DRAW_CHANCE);
    }

    @Test
    void persistsPointsRewardBusinessFields() {
        RewardDefinition reward = new RewardDefinition();
        reward.setRewardCode("POINTS-" + UUID.randomUUID());
        reward.setRewardName("500积分");
        reward.setRewardType(RewardType.POINTS);
        reward.setTargetId(null);
        reward.setQuantity(500L);
        reward.setConfigSnapshot("{\"source\":\"test\"}");
        reward.setStatus(1);
        mapper.insert(reward);
        rewardId = reward.getId();

        RewardDefinition persisted = mapper.selectById(rewardId);

        assertThat(persisted.getRewardCode()).isEqualTo(reward.getRewardCode());
        assertThat(persisted.getRewardName()).isEqualTo("500积分");
        assertThat(persisted.getRewardType()).isEqualTo(RewardType.POINTS);
        assertThat(persisted.getTargetId()).isNull();
        assertThat(persisted.getQuantity()).isEqualTo(500L);
        assertThat(persisted.getConfigSnapshot()).isEqualTo("{\"source\": \"test\"}");
        assertThat(persisted.getStatus()).isOne();
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getUpdatedAt()).isNotNull();
    }
}
