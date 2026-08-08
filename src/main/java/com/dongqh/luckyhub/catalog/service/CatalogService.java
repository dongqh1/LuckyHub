package com.dongqh.luckyhub.catalog.service;

import com.dongqh.luckyhub.catalog.dto.CreateProductCommand;
import com.dongqh.luckyhub.catalog.dto.ProductQuery;
import com.dongqh.luckyhub.catalog.model.RedeemableSkuSnapshot;
import com.dongqh.luckyhub.catalog.model.PurchasableSkuSnapshot;
import com.dongqh.luckyhub.catalog.vo.ProductView;
import com.dongqh.luckyhub.common.result.PageResponse;

import java.util.Optional;

public interface CatalogService {
    ProductView create(CreateProductCommand command);

    ProductView get(long productId);

    PageResponse<ProductView> page(ProductQuery query);

    Optional<RedeemableSkuSnapshot> findRedeemableSku(long skuId);
    Optional<PurchasableSkuSnapshot> findPurchasableSku(long skuId);
}
