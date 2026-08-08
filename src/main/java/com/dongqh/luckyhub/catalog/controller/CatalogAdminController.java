package com.dongqh.luckyhub.catalog.controller;

import com.dongqh.luckyhub.catalog.dto.CreateProductCommand;
import com.dongqh.luckyhub.catalog.service.CatalogService;
import com.dongqh.luckyhub.catalog.vo.ProductView;
import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.rbac.annotation.RequirePermission;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/products")
public class CatalogAdminController {

    private final CatalogService service;

    public CatalogAdminController(CatalogService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCodes.CATALOG_MANAGE)
    public ApiResponse<ProductView> create(@Valid @RequestBody CreateProductCommand command) {
        return ApiResponse.success(service.create(command));
    }
}
