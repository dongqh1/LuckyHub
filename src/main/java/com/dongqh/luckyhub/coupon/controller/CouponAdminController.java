package com.dongqh.luckyhub.coupon.controller;

import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.coupon.dto.CreateCouponTemplateCommand;
import com.dongqh.luckyhub.coupon.dto.IssueCouponCommand;
import com.dongqh.luckyhub.coupon.service.CouponService;
import com.dongqh.luckyhub.coupon.vo.CouponTemplateView;
import com.dongqh.luckyhub.coupon.vo.UserCouponView;
import com.dongqh.luckyhub.rbac.annotation.RequirePermission;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class CouponAdminController {
    private final CouponService service;
    public CouponAdminController(CouponService service) { this.service = service; }

    @PostMapping("/coupon-templates")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCodes.COUPON_MANAGE)
    public ApiResponse<CouponTemplateView> createTemplate(@Valid @RequestBody CreateCouponTemplateCommand command) {
        return ApiResponse.success(service.createTemplate(command));
    }

    @PostMapping("/coupons/issues")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCodes.COUPON_MANAGE)
    public ApiResponse<UserCouponView> issue(@Valid @RequestBody IssueCouponCommand command) {
        return ApiResponse.success(service.issue(command));
    }
}
