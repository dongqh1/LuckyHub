package com.dongqh.luckyhub.catalog;

import com.dongqh.luckyhub.catalog.entity.Product;
import com.dongqh.luckyhub.catalog.entity.ProductSku;
import com.dongqh.luckyhub.catalog.enums.ProductType;
import com.dongqh.luckyhub.catalog.mapper.ProductMapper;
import com.dongqh.luckyhub.catalog.mapper.ProductSkuMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CatalogDomainContractTests {

    @Autowired ProductMapper productMapper;
    @Autowired ProductSkuMapper skuMapper;

    private Long productId;
    private Long skuId;

    @AfterEach
    void cleanUp() {
        if (skuId != null) {
            skuMapper.deleteById(skuId);
        }
        if (productId != null) {
            productMapper.deleteById(productId);
        }
    }

    @Test
    void exposesStableProductTypes() {
        assertThat(ProductType.values()).containsExactly(
                ProductType.PHYSICAL, ProductType.VIRTUAL);
    }

    @Test
    void persistsProductAndSkuBusinessFields() {
        String suffix = UUID.randomUUID().toString();
        Product product = new Product();
        product.setProductCode("PRODUCT-" + suffix);
        product.setProductName("测试保温杯");
        product.setProductType(ProductType.PHYSICAL);
        product.setImageUrl("https://cdn.example/product.png");
        product.setDescription("商品领域契约测试");
        product.setStatus(1);
        productMapper.insert(product);
        productId = product.getId();

        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSkuCode("SKU-" + suffix);
        sku.setSkuName("默认规格");
        sku.setCashPriceCent(12_345L);
        sku.setPointsPrice(9_900L);
        sku.setCashEnabled(true);
        sku.setPointsEnabled(true);
        sku.setStatus(1);
        sku.setVersion(0);
        skuMapper.insert(sku);
        skuId = sku.getId();

        Product persistedProduct = productMapper.selectById(productId);
        ProductSku persistedSku = skuMapper.selectById(skuId);

        assertThat(persistedProduct.getProductCode()).isEqualTo(product.getProductCode());
        assertThat(persistedProduct.getProductName()).isEqualTo("测试保温杯");
        assertThat(persistedProduct.getProductType()).isEqualTo(ProductType.PHYSICAL);
        assertThat(persistedProduct.getImageUrl()).isEqualTo("https://cdn.example/product.png");
        assertThat(persistedProduct.getDescription()).isEqualTo("商品领域契约测试");
        assertThat(persistedProduct.getStatus()).isOne();
        assertThat(persistedProduct.getCreatedAt()).isNotNull();
        assertThat(persistedProduct.getUpdatedAt()).isNotNull();

        assertThat(persistedSku.getProductId()).isEqualTo(productId);
        assertThat(persistedSku.getSkuCode()).isEqualTo(sku.getSkuCode());
        assertThat(persistedSku.getSkuName()).isEqualTo("默认规格");
        assertThat(persistedSku.getCashPriceCent()).isEqualTo(12_345L);
        assertThat(persistedSku.getPointsPrice()).isEqualTo(9_900L);
        assertThat(persistedSku.getCashEnabled()).isTrue();
        assertThat(persistedSku.getPointsEnabled()).isTrue();
        assertThat(persistedSku.getStatus()).isOne();
        assertThat(persistedSku.getVersion()).isZero();
        assertThat(persistedSku.getCreatedAt()).isNotNull();
        assertThat(persistedSku.getUpdatedAt()).isNotNull();
    }
}
