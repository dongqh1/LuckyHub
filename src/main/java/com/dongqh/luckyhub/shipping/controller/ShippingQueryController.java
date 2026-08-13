package com.dongqh.luckyhub.shipping.controller;

import com.dongqh.luckyhub.auth.context.LoginContext;
import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.rbac.annotation.RequirePermission;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import com.dongqh.luckyhub.shipping.service.ShippingQueryService;
import com.dongqh.luckyhub.shipping.vo.ShippingTrackingView;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/shipping/orders")
@RequirePermission(PermissionCodes.SHIPPING_READ)
public class ShippingQueryController {
    private final ShippingQueryService service;

    public ShippingQueryController(ShippingQueryService service) {
        this.service = service;
    }

    @GetMapping("/{shippingNo}")
    public ApiResponse<ShippingTrackingView> get(@PathVariable @Size(max = 64) String shippingNo) {
        return ApiResponse.success(service.getForUser(LoginContext.require().userId(), shippingNo));
    }

    @GetMapping("/{shippingNo}/tracking")
    public ApiResponse<List<ShippingTrackingView.TrackingEvent>> tracking(
            @PathVariable @Size(max = 64) String shippingNo
    ) {
        return ApiResponse.success(service.getForUser(LoginContext.require().userId(), shippingNo).tracking());
    }
}
