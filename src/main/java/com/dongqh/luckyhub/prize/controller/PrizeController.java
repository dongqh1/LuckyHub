package com.dongqh.luckyhub.prize.controller;

import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.prize.dto.CreatePrizeCommand;
import com.dongqh.luckyhub.prize.dto.PrizeQuery;
import com.dongqh.luckyhub.prize.dto.UpdatePrizeCommand;
import com.dongqh.luckyhub.prize.service.PrizeService;
import com.dongqh.luckyhub.prize.vo.PrizeView;
import com.dongqh.luckyhub.rbac.annotation.RequirePermission;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/prizes")
@Tag(name = "奖品管理", description = "奖品创建、查询、修改和禁用接口")
public class PrizeController {

    private final PrizeService service;

    public PrizeController(PrizeService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "创建奖品")
    @RequirePermission(PermissionCodes.PRIZE_CREATE)
    public ApiResponse<PrizeView> create(@Valid @RequestBody CreatePrizeCommand command) {
        return ApiResponse.success(service.create(command));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询奖品详情")
    @RequirePermission(PermissionCodes.PRIZE_READ)
    public ApiResponse<PrizeView> getById(@PathVariable long id) {
        return ApiResponse.success(service.getById(id));
    }

    @GetMapping
    @Operation(summary = "分页查询奖品")
    @RequirePermission(PermissionCodes.PRIZE_READ)
    public ApiResponse<PageResponse<PrizeView>> page(@Valid @ModelAttribute PrizeQuery query) {
        return ApiResponse.success(service.page(query));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改奖品")
    @RequirePermission(PermissionCodes.PRIZE_UPDATE)
    public ApiResponse<PrizeView> update(
            @PathVariable long id,
            @Valid @RequestBody UpdatePrizeCommand command
    ) {
        return ApiResponse.success(service.update(id, command));
    }

    @PatchMapping("/{id}/disable")
    @Operation(summary = "禁用奖品")
    @RequirePermission(PermissionCodes.PRIZE_DISABLE)
    public ApiResponse<Void> disable(@PathVariable long id) {
        service.disable(id);
        return ApiResponse.success();
    }
}
