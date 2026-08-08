package com.dongqh.luckyhub.points.controller;

import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.points.dto.AdminPointsAdjustmentCommand;
import com.dongqh.luckyhub.points.dto.ReversePointsRedemptionCommand;
import com.dongqh.luckyhub.points.service.PointsAccountService;
import com.dongqh.luckyhub.points.service.PointsRedemptionService;
import com.dongqh.luckyhub.points.vo.PointsLedgerView;
import com.dongqh.luckyhub.points.vo.PointsRedemptionView;
import com.dongqh.luckyhub.rbac.annotation.RequirePermission;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/points")
@RequirePermission(PermissionCodes.POINTS_ADJUST)
public class PointsAdminController {

    private final PointsAccountService accountService;
    private final PointsRedemptionService redemptionService;

    public PointsAdminController(
            PointsAccountService accountService,
            PointsRedemptionService redemptionService
    ) {
        this.accountService = accountService;
        this.redemptionService = redemptionService;
    }

    @PostMapping("/adjustments")
    public ApiResponse<PointsLedgerView> adjust(
            @Valid @RequestBody AdminPointsAdjustmentCommand command
    ) {
        return ApiResponse.success(accountService.adjust(command));
    }

    @PostMapping("/redemptions/{redemptionNo}/reverse")
    public ApiResponse<PointsRedemptionView> reverse(
            @PathVariable @Size(min = 1, max = 64) String redemptionNo,
            @Valid @RequestBody ReversePointsRedemptionCommand command
    ) {
        return ApiResponse.success(redemptionService.reverse(redemptionNo, command));
    }
}
