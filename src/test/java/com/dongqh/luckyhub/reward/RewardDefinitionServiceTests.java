package com.dongqh.luckyhub.reward;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dongqh.luckyhub.catalog.entity.Product;
import com.dongqh.luckyhub.catalog.entity.ProductSku;
import com.dongqh.luckyhub.catalog.enums.ProductType;
import com.dongqh.luckyhub.catalog.mapper.ProductMapper;
import com.dongqh.luckyhub.catalog.mapper.ProductSkuMapper;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.reward.dto.CreateRewardDefinitionCommand;
import com.dongqh.luckyhub.reward.entity.RewardDefinition;
import com.dongqh.luckyhub.reward.enums.RewardErrorCode;
import com.dongqh.luckyhub.reward.enums.RewardType;
import com.dongqh.luckyhub.reward.mapper.RewardDefinitionMapper;
import com.dongqh.luckyhub.reward.service.RewardDefinitionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class RewardDefinitionServiceTests {

    @Autowired
    private RewardDefinitionService service;

    @Autowired
    private RewardDefinitionMapper rewardMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductSkuMapper skuMapper;

    @BeforeEach
    void cleanTables() {
        rewardMapper.delete(new LambdaQueryWrapper<>());
        skuMapper.delete(new LambdaQueryWrapper<>());
        productMapper.delete(new LambdaQueryWrapper<>());
    }

    @Test
    void createsAllFiveRewardTypesAndNormalizesJson() {
        long skuId = createSku(1);
        List<CreateRewardDefinitionCommand> commands = List.of(
                command("PRODUCT-1", RewardType.PRODUCT, skuId, " { \"source\" : \"mall\" } "),
                command("COUPON-1", RewardType.COUPON, 2001L, null),
                command("POINTS-1", RewardType.POINTS, null, "   "),
                command("MEMBER-1", RewardType.MEMBERSHIP, 3001L, null),
                command("DRAW-1", RewardType.DRAW_CHANCE, null, null)
        );

        var views = commands.stream().map(service::create).toList();

        assertThat(views).extracting(view -> view.rewardType())
                .containsExactly(RewardType.PRODUCT, RewardType.COUPON, RewardType.POINTS,
                        RewardType.MEMBERSHIP, RewardType.DRAW_CHANCE);
        assertThat(views.get(0).configSnapshot()).isEqualTo("{\"source\":\"mall\"}");
        assertThat(views.get(2).configSnapshot()).isNull();
        assertThat(service.get(views.get(0).id()).rewardCode()).isEqualTo("PRODUCT-1");
        assertThat(rewardMapper.selectCount(new LambdaQueryWrapper<>())).isEqualTo(5);
    }

    @Test
    void rejectsMissingOrUnexpectedTargets() {
        for (RewardType type : List.of(RewardType.PRODUCT, RewardType.COUPON, RewardType.MEMBERSHIP)) {
            assertTargetInvalid(command("MISSING-" + type, type, null, null));
        }
        for (RewardType type : List.of(RewardType.POINTS, RewardType.DRAW_CHANCE)) {
            assertTargetInvalid(command("UNEXPECTED-" + type, type, 99L, null));
        }
    }

    @Test
    void productRewardRequiresAnEnabledSku() {
        assertTargetInvalid(command("NO-SKU", RewardType.PRODUCT, Long.MAX_VALUE, null));

        long disabledSkuId = createSku(0);
        assertTargetInvalid(command("DISABLED-SKU", RewardType.PRODUCT, disabledSkuId, null));
    }

    @Test
    void translatesDuplicateCodeAndTrimsBusinessStrings() {
        var created = service.create(new CreateRewardDefinitionCommand(
                " POINTS-500 ", " 500积分 ", RewardType.POINTS, null, 500L, null));

        assertThat(created.rewardCode()).isEqualTo("POINTS-500");
        assertThat(created.rewardName()).isEqualTo("500积分");
        assertThatThrownBy(() -> service.create(command("POINTS-500", RewardType.POINTS, null, null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(RewardErrorCode.REWARD_CODE_DUPLICATE));
    }

    @Test
    void rejectsMalformedJsonAndMissingDefinitionWithStableErrors() {
        assertThatThrownBy(() -> service.create(command(
                "BAD-JSON", RewardType.POINTS, null, "{not-json}")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(RewardErrorCode.REWARD_CONFIG_INVALID));

        assertThatThrownBy(() -> service.get(Long.MAX_VALUE))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(RewardErrorCode.REWARD_NOT_FOUND));
    }

    private void assertTargetInvalid(CreateRewardDefinitionCommand command) {
        assertThatThrownBy(() -> service.create(command))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(RewardErrorCode.REWARD_TARGET_INVALID));
    }

    private CreateRewardDefinitionCommand command(String code, RewardType type, Long targetId, String config) {
        return new CreateRewardDefinitionCommand(code, "测试奖励", type, targetId, 1L, config);
    }

    private long createSku(int status) {
        Product product = new Product();
        product.setProductCode("PROD-" + status + "-" + System.nanoTime());
        product.setProductName("测试商品");
        product.setProductType(ProductType.PHYSICAL);
        product.setStatus(1);
        productMapper.insert(product);

        ProductSku sku = new ProductSku();
        sku.setProductId(product.getId());
        sku.setSkuCode("SKU-" + status + "-" + System.nanoTime());
        sku.setSkuName("默认SKU");
        sku.setCashPriceCent(100L);
        sku.setCashEnabled(true);
        sku.setPointsEnabled(false);
        sku.setStatus(status);
        sku.setVersion(0);
        skuMapper.insert(sku);
        return sku.getId();
    }
}
