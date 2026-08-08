package com.dongqh.luckyhub.payment.controller;

import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.payment.dto.PaymentCallbackCommand;
import com.dongqh.luckyhub.payment.dto.SimulatePaymentCommand;
import com.dongqh.luckyhub.payment.service.PaymentService;
import com.dongqh.luckyhub.payment.vo.PaymentView;
import com.dongqh.luckyhub.rbac.annotation.RequirePermission;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/payments")
public class PaymentSimulationController {
    private final PaymentService service;
    public PaymentSimulationController(PaymentService service) { this.service = service; }

    @PostMapping("/{paymentNo}/simulate")
    @RequirePermission(PermissionCodes.PAYMENT_SIMULATE)
    public ApiResponse<PaymentView> simulate(@PathVariable String paymentNo,
                                             @Valid @RequestBody SimulatePaymentCommand command) {
        // Amount is deliberately resolved by the service through a trusted payment record.
        return ApiResponse.success(service.simulate(paymentNo, command.result(), command.failureReason()));
    }
}
