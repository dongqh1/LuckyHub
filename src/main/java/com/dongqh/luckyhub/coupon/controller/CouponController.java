package com.dongqh.luckyhub.coupon.controller;

import com.dongqh.luckyhub.auth.context.LoginContext;
import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.coupon.service.CouponService;
import com.dongqh.luckyhub.coupon.vo.UserCouponView;
import com.dongqh.luckyhub.rbac.annotation.RequirePermission;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/coupons")
@RequirePermission(PermissionCodes.COUPON_READ)
public class CouponController {
    private final CouponService service;
    public CouponController(CouponService service) { this.service = service; }

    @GetMapping
    public ApiResponse<PageResponse<UserCouponView>> page(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size) {
        return ApiResponse.success(service.pageMine(LoginContext.require().userId(), page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserCouponView> get(@PathVariable @Positive long id) {
        return ApiResponse.success(service.getMine(LoginContext.require().userId(), id));
    }
}
