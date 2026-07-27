package com.dongqh.luckyhub.prize.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dongqh.luckyhub.prize.entity.MarketingPrize;
import com.dongqh.luckyhub.prize.enums.PrizeLevel;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PrizeMapperTests {

    private final MarketingPrizeMapper mapper;

    @Autowired
    PrizeMapperTests(MarketingPrizeMapper mapper) {
        this.mapper = mapper;
    }

    @Test
    void paginatesPrizeRows() {
        mapper.insert(prize("分页奖品A"));
        mapper.insert(prize("分页奖品B"));

        Page<MarketingPrize> result = mapper.selectPage(
                new Page<>(2, 1),
                new LambdaQueryWrapper<MarketingPrize>()
                        .likeRight(MarketingPrize::getPrizeName, "分页奖品")
                        .orderByAsc(MarketingPrize::getId)
        );

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getPrizeName()).isEqualTo("分页奖品B");
    }

    private MarketingPrize prize(String name) {
        MarketingPrize prize = new MarketingPrize();
        prize.setPrizeName(name);
        prize.setPrizeType(PrizeType.COUPON);
        prize.setPrizeLevel(PrizeLevel.FIRST);
        prize.setStackable(false);
        prize.setStatus(1);
        return prize;
    }
}
