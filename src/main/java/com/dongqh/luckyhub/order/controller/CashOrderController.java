package com.dongqh.luckyhub.order.controller;

import com.dongqh.luckyhub.auth.context.LoginContext;
import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.order.dto.*;
import com.dongqh.luckyhub.order.service.CashOrderService;
import com.dongqh.luckyhub.order.vo.CashOrderView;
import com.dongqh.luckyhub.rbac.annotation.RequirePermission;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class CashOrderController {
    private final CashOrderService service;
    public CashOrderController(CashOrderService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCodes.ORDER_CREATE)
    public ApiResponse<CashOrderView> create(@Valid @RequestBody CreateCashOrderCommand command) {
        return ApiResponse.success(service.create(LoginContext.require().userId(), command));
    }

    @GetMapping
    @RequirePermission(PermissionCodes.ORDER_READ)
    public ApiResponse<PageResponse<CashOrderView>> page(@Valid @ModelAttribute CashOrderQuery query) {
        return ApiResponse.success(service.page(LoginContext.require().userId(), query));
    }

    @GetMapping("/{orderNo}")
    @RequirePermission(PermissionCodes.ORDER_READ)
    public ApiResponse<CashOrderView> get(@PathVariable String orderNo) {
        return ApiResponse.success(service.get(LoginContext.require().userId(), orderNo));
    }

    @PostMapping("/{orderNo}/cancel")
    @RequirePermission(PermissionCodes.ORDER_CANCEL)
    public ApiResponse<CashOrderView> cancel(@PathVariable String orderNo,
                                              @Valid @RequestBody CancelCashOrderCommand command) {
        return ApiResponse.success(service.cancel(LoginContext.require().userId(), orderNo, command.reason()));
    }
}
