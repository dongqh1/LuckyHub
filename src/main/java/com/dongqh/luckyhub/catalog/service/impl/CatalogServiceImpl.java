package com.dongqh.luckyhub.catalog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dongqh.luckyhub.catalog.dto.CreateProductCommand;
import com.dongqh.luckyhub.catalog.dto.ProductQuery;
import com.dongqh.luckyhub.catalog.entity.Product;
import com.dongqh.luckyhub.catalog.entity.ProductSku;
import com.dongqh.luckyhub.catalog.enums.ProductErrorCode;
import com.dongqh.luckyhub.catalog.mapper.ProductMapper;
import com.dongqh.luckyhub.catalog.mapper.ProductSkuMapper;
import com.dongqh.luckyhub.catalog.model.RedeemableSkuSnapshot;
import com.dongqh.luckyhub.catalog.service.CatalogService;
import com.dongqh.luckyhub.catalog.vo.ProductView;
import com.dongqh.luckyhub.catalog.vo.SkuView;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.common.result.PageResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CatalogServiceImpl implements CatalogService {

    private static final int ENABLED = 1;
    private static final int INITIAL_VERSION = 0;

    private final ProductMapper productMapper;
    private final ProductSkuMapper skuMapper;

    public CatalogServiceImpl(ProductMapper productMapper, ProductSkuMapper skuMapper) {
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
    }

    @Override
    @Transactional
    public ProductView create(CreateProductCommand command) {
        Product product = new Product();
        product.setProductCode(command.productCode().trim());
        product.setProductName(command.productName().trim());
        product.setProductType(command.productType());
        product.setImageUrl(normalize(command.imageUrl()));
        product.setDescription(normalize(command.description()));
        product.setStatus(ENABLED);

        ProductSku sku = new ProductSku();
        sku.setSkuCode(command.skuCode().trim());
        sku.setSkuName(command.skuName().trim());
        sku.setCashPriceCent(command.cashPriceCent());
        sku.setPointsPrice(command.pointsPrice());
        sku.setCashEnabled(command.cashEnabled());
        sku.setPointsEnabled(command.pointsEnabled());
        sku.setStatus(ENABLED);
        sku.setVersion(INITIAL_VERSION);

        try {
            productMapper.insert(product);
            sku.setProductId(product.getId());
            skuMapper.insert(sku);
        } catch (DuplicateKeyException exception) {
            Product existing = productMapper.selectOne(new LambdaQueryWrapper<Product>()
                    .eq(Product::getProductCode, product.getProductCode()));
            if (existing != null && !existing.getId().equals(product.getId())) {
                throw new BusinessException(ProductErrorCode.PRODUCT_CODE_DUPLICATE);
            }
            throw new BusinessException(ProductErrorCode.SKU_CODE_DUPLICATE);
        }
        return toView(product, List.of(sku));
    }

    @Override
    public ProductView get(long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null || !Integer.valueOf(ENABLED).equals(product.getStatus())) {
            throw new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND);
        }
        return toView(product, findEnabledSkus(productId));
    }

    @Override
    public PageResponse<ProductView> page(ProductQuery query) {
        String name = normalize(query.getName());
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .like(name != null, Product::getProductName, name)
                .eq(query.getType() != null, Product::getProductType, query.getType())
                .eq(Product::getStatus, ENABLED)
                .orderByDesc(Product::getCreatedAt)
                .orderByDesc(Product::getId);
        Page<Product> result = productMapper.selectPage(new Page<>(query.getPage(), query.getSize()), wrapper);
        Map<Long, List<ProductSku>> skusByProduct = findEnabledSkus(result.getRecords());
        List<ProductView> records = result.getRecords().stream()
                .map(product -> toView(product, skusByProduct.getOrDefault(product.getId(), List.of())))
                .toList();
        return new PageResponse<>(records, result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    @Override
    public Optional<RedeemableSkuSnapshot> findRedeemableSku(long skuId) {
        ProductSku sku = skuMapper.selectById(skuId);
        if (sku == null || !Integer.valueOf(ENABLED).equals(sku.getStatus())
                || !Boolean.TRUE.equals(sku.getPointsEnabled())
                || sku.getPointsPrice() == null || sku.getPointsPrice() <= 0) {
            return Optional.empty();
        }
        Product product = productMapper.selectById(sku.getProductId());
        if (product == null || !Integer.valueOf(ENABLED).equals(product.getStatus())) {
            return Optional.empty();
        }
        return Optional.of(new RedeemableSkuSnapshot(
                sku.getId(), product.getProductCode(), product.getProductName(),
                sku.getSkuCode(), sku.getSkuName(), product.getProductType(),
                product.getImageUrl(), sku.getPointsPrice()));
    }

    private List<ProductSku> findEnabledSkus(long productId) {
        return skuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getProductId, productId)
                .eq(ProductSku::getStatus, ENABLED)
                .orderByAsc(ProductSku::getId));
    }

    private Map<Long, List<ProductSku>> findEnabledSkus(List<Product> products) {
        if (products.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> productIds = products.stream().map(Product::getId).toList();
        return skuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                        .in(ProductSku::getProductId, productIds)
                        .eq(ProductSku::getStatus, ENABLED)
                        .orderByAsc(ProductSku::getId))
                .stream()
                .collect(Collectors.groupingBy(ProductSku::getProductId));
    }

    private ProductView toView(Product product, List<ProductSku> skus) {
        List<SkuView> skuViews = skus.stream().map(this::toSkuView).toList();
        return new ProductView(product.getId(), product.getProductCode(), product.getProductName(),
                product.getProductType(), product.getImageUrl(), product.getDescription(), product.getStatus(),
                skuViews, product.getCreatedAt(), product.getUpdatedAt());
    }

    private SkuView toSkuView(ProductSku sku) {
        return new SkuView(sku.getId(), sku.getProductId(), sku.getSkuCode(), sku.getSkuName(),
                sku.getCashPriceCent(), sku.getPointsPrice(), sku.getCashEnabled(), sku.getPointsEnabled(),
                sku.getStatus(), sku.getVersion(), sku.getCreatedAt(), sku.getUpdatedAt());
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
