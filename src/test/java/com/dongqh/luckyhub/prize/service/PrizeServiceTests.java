package com.dongqh.luckyhub.prize.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.prize.dto.CreatePrizeCommand;
import com.dongqh.luckyhub.prize.dto.PrizeQuery;
import com.dongqh.luckyhub.prize.dto.UpdatePrizeCommand;
import com.dongqh.luckyhub.prize.entity.MarketingPrize;
import com.dongqh.luckyhub.prize.enums.PrizeErrorCode;
import com.dongqh.luckyhub.prize.enums.PrizeLevel;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import com.dongqh.luckyhub.prize.mapper.MarketingPrizeMapper;
import com.dongqh.luckyhub.prize.service.impl.PrizeServiceImpl;
import com.dongqh.luckyhub.reward.entity.RewardDefinition;
import com.dongqh.luckyhub.reward.enums.RewardType;
import com.dongqh.luckyhub.reward.mapper.RewardDefinitionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrizeServiceTests {

    @Mock
    private MarketingPrizeMapper mapper;
    @Mock private RewardDefinitionMapper rewards;

    private PrizeService service;

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                MarketingPrize.class
        );
    }

    @BeforeEach
    void setUp() {
        service = new PrizeServiceImpl(mapper, rewards);
    }

    @Test
    void createsEnabledPrizeAndReturnsView() {
        when(rewards.selectById(7L)).thenReturn(reward(7L, RewardType.COUPON));
        when(mapper.insert(any(MarketingPrize.class))).thenAnswer(invocation -> {
            MarketingPrize prize = invocation.getArgument(0);
            prize.setId(9L);
            return 1;
        });

        var view = service.create(new CreatePrizeCommand(
                "  咖啡券  ",
                PrizeType.COUPON,
                PrizeLevel.FIRST,
                " https://cdn.example/prize.jpg ",
                " 说明 ",
                true, 7L
        ));

        ArgumentCaptor<MarketingPrize> captor = ArgumentCaptor.forClass(MarketingPrize.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(1);
        assertThat(view.id()).isEqualTo(9L);
        assertThat(view.prizeName()).isEqualTo("咖啡券");
        assertThat(view.imageUrl()).isEqualTo("https://cdn.example/prize.jpg");
        assertThat(view.description()).isEqualTo("说明");
    }

    @Test
    void returnsPrizeDetail() {
        when(mapper.selectById(1L)).thenReturn(prize(1L, 1));

        assertThat(service.getById(1L).id()).isEqualTo(1L);
    }

    @Test
    void rejectsMissingPrize() {
        when(mapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.getById(404L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(PrizeErrorCode.PRIZE_NOT_FOUND));
    }

    @Test
    void updatesEditableFieldsAndKeepsStatus() {
        MarketingPrize existing = prize(2L, 1);
        when(mapper.selectById(2L)).thenReturn(existing);
        when(mapper.updateById(existing)).thenReturn(1);

        var view = service.update(2L, new UpdatePrizeCommand(
                "新奖品",
                PrizeType.PHYSICAL,
                PrizeLevel.SECOND,
                "",
                "新说明",
                false
        ));

        verify(mapper).updateById(existing);
        assertThat(view.prizeName()).isEqualTo("新奖品");
        assertThat(view.imageUrl()).isNull();
        assertThat(view.status()).isEqualTo(1);
    }

    private RewardDefinition reward(long id, RewardType type) {
        RewardDefinition reward = new RewardDefinition(); reward.setId(id);
        reward.setRewardType(type); reward.setStatus(1); return reward;
    }

    @Test
    void disablesEnabledPrize() {
        MarketingPrize existing = prize(3L, 1);
        when(mapper.selectById(3L)).thenReturn(existing);
        when(mapper.updateById(existing)).thenReturn(1);

        service.disable(3L);

        assertThat(existing.getStatus()).isZero();
        verify(mapper).updateById(existing);
    }

    @Test
    void disablingDisabledPrizeIsIdempotent() {
        MarketingPrize existing = prize(4L, 0);
        when(mapper.selectById(4L)).thenReturn(existing);

        service.disable(4L);

        verify(mapper, never()).updateById(any(MarketingPrize.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void queriesFilteredPrizePage() {
        Page<MarketingPrize> result = new Page<>(2, 20, 21);
        result.setRecords(java.util.List.of(prize(5L, 1)));
        when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(result);
        PrizeQuery query = new PrizeQuery();
        query.setPage(2);
        query.setName("咖啡");
        query.setType(PrizeType.COUPON);
        query.setStatus(1);

        var response = service.page(query);

        ArgumentCaptor<Wrapper<MarketingPrize>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectPage(any(Page.class), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getCustomSqlSegment())
                .contains("prize_name", "prize_type", "status", "created_at", "id");
        assertThat(response.records()).hasSize(1);
        assertThat(response.total()).isEqualTo(21);
        assertThat(response.pages()).isEqualTo(2);
    }

    private MarketingPrize prize(long id, int status) {
        MarketingPrize prize = new MarketingPrize();
        prize.setId(id);
        prize.setPrizeName("原奖品");
        prize.setPrizeType(PrizeType.POINTS);
        prize.setPrizeLevel(PrizeLevel.THIRD);
        prize.setStackable(true);
        prize.setStatus(status);
        return prize;
    }
}
