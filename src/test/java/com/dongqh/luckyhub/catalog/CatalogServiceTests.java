package com.dongqh.luckyhub.catalog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dongqh.luckyhub.catalog.dto.CreateProductCommand;
import com.dongqh.luckyhub.catalog.dto.ProductQuery;
import com.dongqh.luckyhub.catalog.entity.Product;
import com.dongqh.luckyhub.catalog.entity.ProductSku;
import com.dongqh.luckyhub.catalog.enums.ProductErrorCode;
import com.dongqh.luckyhub.catalog.enums.ProductType;
import com.dongqh.luckyhub.catalog.mapper.ProductMapper;
import com.dongqh.luckyhub.catalog.mapper.ProductSkuMapper;
import com.dongqh.luckyhub.catalog.service.CatalogService;
import com.dongqh.luckyhub.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class CatalogServiceTests {

    @Autowired
    private CatalogService service;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductSkuMapper skuMapper;

    @BeforeEach
    void cleanTables() {
        skuMapper.delete(new LambdaQueryWrapper<>());
        productMapper.delete(new LambdaQueryWrapper<>());
    }

    @Test
    void commandAcceptsEveryPurchaseModeAndRejectsInvalidCombinations() {
        assertThat(command("P-CASH", "S-CASH", true, false)).isNotNull();
        assertThat(command("P-POINTS", "S-POINTS", false, true)).isNotNull();
        assertThat(command("P-BOTH", "S-BOTH", true, true)).isNotNull();

        assertThatThrownBy(() -> new CreateProductCommand(
                "P1", "商品", ProductType.PHYSICAL, null, null,
                "S1", "SKU", null, 100L, true, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CreateProductCommand(
                "P1", "商品", ProductType.PHYSICAL, null, null,
                "S1", "SKU", 100L, null, true, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CreateProductCommand(
                "P1", "商品", ProductType.PHYSICAL, null, null,
                "S1", "SKU", null, null, false, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createsOneProductAndOneDefaultSkuAndReturnsSnapshots() {
        var created = service.create(command("  PROD-1 ", " SKU-1 ", true, true));

        assertThat(productMapper.selectCount(new LambdaQueryWrapper<>())).isEqualTo(1);
        assertThat(skuMapper.selectCount(new LambdaQueryWrapper<>())).isEqualTo(1);
        assertThat(created.productCode()).isEqualTo("PROD-1");
        assertThat(created.skus()).singleElement().satisfies(sku -> {
            assertThat(sku.skuCode()).isEqualTo("SKU-1");
            assertThat(sku.cashPriceCent()).isEqualTo(1999L);
            assertThat(sku.pointsPrice()).isEqualTo(2000L);
            assertThat(sku.cashEnabled()).isTrue();
            assertThat(sku.pointsEnabled()).isTrue();
        });
        var detail = service.get(created.id());
        assertThat(detail.productCode()).isEqualTo(created.productCode());
        assertThat(detail.productName()).isEqualTo(created.productName());
        assertThat(detail.skus()).extracting(sku -> sku.skuCode()).containsExactly("SKU-1");
        assertThat(detail.createdAt()).isNotNull();
        assertThat(detail.skus().get(0).createdAt()).isNotNull();
    }

    @Test
    void translatesDuplicateProductAndSkuCodesAndRollsBackPartialProduct() {
        service.create(command("PROD-1", "SKU-1", true, false));

        assertThatThrownBy(() -> service.create(command(" PROD-1 ", "SKU-2", true, false)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ProductErrorCode.PRODUCT_CODE_DUPLICATE));

        assertThatThrownBy(() -> service.create(command("PROD-2", " SKU-1 ", true, false)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ProductErrorCode.SKU_CODE_DUPLICATE));

        assertThat(productMapper.selectCount(new LambdaQueryWrapper<>())).isEqualTo(1);
        assertThat(productMapper.selectOne(new LambdaQueryWrapper<Product>()
                .eq(Product::getProductCode, "PROD-2"))).isNull();
    }

    @Test
    void userPageHidesDisabledProducts() {
        var enabled = service.create(command("PROD-1", "SKU-1", true, false));
        var disabled = service.create(command("PROD-2", "SKU-2", false, true));
        Product entity = productMapper.selectById(disabled.id());
        entity.setStatus(0);
        productMapper.updateById(entity);

        ProductQuery query = new ProductQuery();
        var page = service.page(query);

        assertThat(page.records()).extracting(view -> view.id()).containsExactly(enabled.id());
        assertThat(page.records().get(0).skus()).hasSize(1);
    }

    @Test
    void missingProductUsesStableError() {
        assertThatThrownBy(() -> service.get(Long.MAX_VALUE))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND));
    }

    private CreateProductCommand command(String productCode, String skuCode,
                                         boolean cashEnabled, boolean pointsEnabled) {
        return new CreateProductCommand(
                productCode,
                " 测试商品 ",
                ProductType.PHYSICAL,
                " https://cdn.example/product.png ",
                " 商品说明 ",
                skuCode,
                " 默认SKU ",
                cashEnabled ? 1999L : null,
                pointsEnabled ? 2000L : null,
                cashEnabled,
                pointsEnabled
        );
    }
}
