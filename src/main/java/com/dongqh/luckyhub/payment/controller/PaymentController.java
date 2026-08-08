package com.dongqh.luckyhub.payment.controller;

import com.dongqh.luckyhub.auth.context.LoginContext;
import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.payment.dto.CreatePaymentCommand;
import com.dongqh.luckyhub.payment.service.PaymentService;
import com.dongqh.luckyhub.payment.vo.PaymentView;
import com.dongqh.luckyhub.rbac.annotation.RequirePermission;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentService service;
    public PaymentController(PaymentService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCodes.PAYMENT_CREATE)
    public ApiResponse<PaymentView> create(@Valid @RequestBody CreatePaymentCommand command) {
        return ApiResponse.success(service.create(LoginContext.require().userId(), command));
    }
}
