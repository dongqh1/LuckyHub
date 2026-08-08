package com.dongqh.luckyhub.points.controller;

import com.dongqh.luckyhub.auth.context.LoginContext;
import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.points.dto.CreatePointsRedemptionCommand;
import com.dongqh.luckyhub.points.dto.PointsRedemptionQuery;
import com.dongqh.luckyhub.points.service.PointsRedemptionService;
import com.dongqh.luckyhub.points.vo.PointsRedemptionView;
import com.dongqh.luckyhub.rbac.annotation.RequirePermission;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/points/redemptions")
public class PointsRedemptionController {

    private final PointsRedemptionService service;

    public PointsRedemptionController(PointsRedemptionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCodes.POINTS_REDEEM)
    public ApiResponse<PointsRedemptionView> create(
            @Valid @RequestBody CreatePointsRedemptionCommand command
    ) {
        return ApiResponse.success(service.create(LoginContext.require().userId(), command));
    }

    @GetMapping
    @RequirePermission(PermissionCodes.POINTS_READ)
    public ApiResponse<PageResponse<PointsRedemptionView>> page(
            @Valid @ModelAttribute PointsRedemptionQuery query
    ) {
        return ApiResponse.success(service.page(LoginContext.require().userId(), query));
    }

    @GetMapping("/{redemptionNo}")
    @RequirePermission(PermissionCodes.POINTS_READ)
    public ApiResponse<PointsRedemptionView> get(
            @PathVariable @Size(min = 1, max = 64) String redemptionNo
    ) {
        return ApiResponse.success(service.get(LoginContext.require().userId(), redemptionNo));
    }
}
