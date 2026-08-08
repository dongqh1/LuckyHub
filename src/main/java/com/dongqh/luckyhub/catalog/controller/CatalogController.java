package com.dongqh.luckyhub.catalog.controller;

import com.dongqh.luckyhub.catalog.dto.ProductQuery;
import com.dongqh.luckyhub.catalog.service.CatalogService;
import com.dongqh.luckyhub.catalog.vo.ProductView;
import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.rbac.annotation.RequirePermission;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/products")
public class CatalogController {

    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePermission(PermissionCodes.CATALOG_READ)
    public ApiResponse<PageResponse<ProductView>> page(@Valid @ModelAttribute ProductQuery query) {
        return ApiResponse.success(service.page(query));
    }

    @GetMapping("/{id}")
    @RequirePermission(PermissionCodes.CATALOG_READ)
    public ApiResponse<ProductView> get(@PathVariable @Positive long id) {
        return ApiResponse.success(service.get(id));
    }
}
