package com.dongqh.luckyhub.prize;

import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.prize.dto.CreatePrizeCommand;
import com.dongqh.luckyhub.prize.dto.UpdatePrizeCommand;
import com.dongqh.luckyhub.prize.enums.PrizeLevel;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import com.dongqh.luckyhub.prize.service.PrizeService;
import com.dongqh.luckyhub.prize.vo.PrizeView;
import com.dongqh.luckyhub.reward.enums.RewardType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PrizeRewardBindingTests {
    @Autowired PrizeService prizes;
    @Autowired JdbcTemplate jdbc;

    @Test
    void newPrizeRequiresCompatibleEnabledRewardBinding() {
        String code = "PHASE5-BOUND-POINTS";
        jdbc.update("DELETE FROM marketing_prize WHERE prize_name='阶段5绑定测试'");
        jdbc.update("DELETE FROM reward_definition WHERE reward_code=?", code);
        jdbc.update("""
                INSERT INTO reward_definition(reward_code,reward_name,reward_type,target_id,quantity,status)
                VALUES(?,?,'POINTS',NULL,100,1)
                """, code, "100积分");
        Long rewardId = jdbc.queryForObject(
                "SELECT id FROM reward_definition WHERE reward_code=?", Long.class, code);
        try {
            PrizeView view = prizes.create(new CreatePrizeCommand(
                    "阶段5绑定测试", PrizeType.POINTS, PrizeLevel.FIRST,
                    null, "绑定统一奖励", false, rewardId));
            assertThat(view.rewardDefinitionId()).isEqualTo(rewardId);
            assertThat(view.rewardType()).isEqualTo(RewardType.POINTS);

            assertThatThrownBy(() -> prizes.create(new CreatePrizeCommand(
                    "错误类型", PrizeType.COUPON, PrizeLevel.FIRST,
                    null, null, false, rewardId)))
                    .isInstanceOf(BusinessException.class);
        } finally {
            jdbc.update("DELETE FROM marketing_prize WHERE prize_name IN ('阶段5绑定测试','错误类型')");
            jdbc.update("DELETE FROM reward_definition WHERE reward_code=?", code);
        }
    }

    @Test
    void legacyPrizeCanBindOnceButBoundPrizeCannotUnbindOrRebind() {
        jdbc.update("DELETE FROM marketing_prize WHERE prize_name='阶段5旧奖品'");
        jdbc.update("DELETE FROM reward_definition WHERE reward_code IN ('P5-BIND-A','P5-BIND-B')");
        jdbc.update("INSERT INTO reward_definition(reward_code,reward_name,reward_type,quantity,status) VALUES('P5-BIND-A','A','POINTS',10,1)");
        jdbc.update("INSERT INTO reward_definition(reward_code,reward_name,reward_type,quantity,status) VALUES('P5-BIND-B','B','POINTS',20,1)");
        Long rewardA = jdbc.queryForObject("SELECT id FROM reward_definition WHERE reward_code='P5-BIND-A'", Long.class);
        Long rewardB = jdbc.queryForObject("SELECT id FROM reward_definition WHERE reward_code='P5-BIND-B'", Long.class);
        jdbc.update("""
                INSERT INTO marketing_prize(prize_name,prize_type,prize_level,stackable,status,reward_definition_id)
                VALUES('阶段5旧奖品','POINTS','FIRST',0,1,NULL)
                """);
        Long prizeId = jdbc.queryForObject("SELECT id FROM marketing_prize WHERE prize_name='阶段5旧奖品'", Long.class);
        try {
            PrizeView bound = prizes.update(prizeId, update(rewardA));
            assertThat(bound.rewardDefinitionId()).isEqualTo(rewardA);

            PrizeView preserved = prizes.update(prizeId, update(null));
            assertThat(preserved.rewardDefinitionId()).isEqualTo(rewardA);

            assertThatThrownBy(() -> prizes.update(prizeId, update(rewardB)))
                    .isInstanceOf(BusinessException.class);
        } finally {
            jdbc.update("DELETE FROM marketing_prize WHERE id=?", prizeId);
            jdbc.update("DELETE FROM reward_definition WHERE reward_code IN ('P5-BIND-A','P5-BIND-B')");
        }
    }

    private UpdatePrizeCommand update(Long rewardId) {
        return new UpdatePrizeCommand("阶段5旧奖品", PrizeType.POINTS, PrizeLevel.FIRST,
                null, "首次绑定后不可换绑", false, rewardId);
    }
}
