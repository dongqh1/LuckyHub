package com.dongqh.luckyhub.points.controller;

import com.dongqh.luckyhub.auth.context.LoginContext;
import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.points.dto.PointsLedgerQuery;
import com.dongqh.luckyhub.points.service.PointsQueryService;
import com.dongqh.luckyhub.points.vo.PointsAccountView;
import com.dongqh.luckyhub.points.vo.PointsLedgerView;
import com.dongqh.luckyhub.rbac.annotation.RequirePermission;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/points")
@RequirePermission(PermissionCodes.POINTS_READ)
public class PointsController {

    private final PointsQueryService service;

    public PointsController(PointsQueryService service) {
        this.service = service;
    }

    @GetMapping("/account")
    public ApiResponse<PointsAccountView> account() {
        return ApiResponse.success(service.getAccount(LoginContext.require().userId()));
    }

    @GetMapping("/ledgers")
    public ApiResponse<PageResponse<PointsLedgerView>> ledgers(
            @Valid @ModelAttribute PointsLedgerQuery query
    ) {
        return ApiResponse.success(service.pageLedgers(LoginContext.require().userId(), query));
    }
}
