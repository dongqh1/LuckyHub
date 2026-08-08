package com.dongqh.luckyhub.payment.controller;

import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.payment.dto.PaymentCallbackCommand;
import com.dongqh.luckyhub.payment.service.PaymentService;
import com.dongqh.luckyhub.payment.vo.PaymentView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/callbacks/payments")
public class PaymentCallbackController {
    private final PaymentService service;
    public PaymentCallbackController(PaymentService service) { this.service = service; }

    @PostMapping
    public ApiResponse<PaymentView> callback(@Valid @RequestBody PaymentCallbackCommand command) {
        return ApiResponse.success(service.callback(command));
    }
}
