package com.dongqh.luckyhub.shipping.controller;

import com.dongqh.luckyhub.auth.context.LoginContext;
import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.rbac.annotation.RequirePermission;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import com.dongqh.luckyhub.shipping.dto.ShippingOperationCommand;
import com.dongqh.luckyhub.shipping.dto.ShippingOrderQuery;
import com.dongqh.luckyhub.shipping.service.ShippingAdminService;
import com.dongqh.luckyhub.shipping.vo.ShippingOrderView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/shipping/orders")
@RequirePermission(PermissionCodes.SHIPPING_OPERATE)
public class ShippingAdminController {
    private final ShippingAdminService service;
    public ShippingAdminController(ShippingAdminService service) { this.service = service; }

    @GetMapping
    public ApiResponse<PageResponse<ShippingOrderView>> page(@Valid @ModelAttribute ShippingOrderQuery query) {
        return ApiResponse.success(service.page(query));
    }
    @GetMapping("/{shippingNo}")
    public ApiResponse<ShippingOrderView> get(@PathVariable String shippingNo) {
        return ApiResponse.success(service.get(shippingNo));
    }
    @PostMapping("/{shippingNo}/retry")
    public ApiResponse<ShippingOrderView> retry(@PathVariable String shippingNo,
                                                @RequestBody(required = false) ShippingOperationCommand command) {
        return ApiResponse.success(service.retry(shippingNo, LoginContext.require().userId(),
                command == null ? null : command.note()));
    }
    @PostMapping("/{shippingNo}/terminate")
    public ApiResponse<ShippingOrderView> terminate(@PathVariable String shippingNo,
                                                    @RequestBody(required = false) ShippingOperationCommand command) {
        return ApiResponse.success(service.terminate(shippingNo, LoginContext.require().userId(),
                command == null ? null : command.note()));
    }
}
