package com.dongqh.luckyhub.payment.dto;

import com.dongqh.luckyhub.payment.enums.PaymentResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PaymentCallbackCommand(
        @NotBlank @Size(max = 64) String paymentNo,
        @NotNull PaymentResult result,
        @Size(max = 255) String failureReason,
        @NotBlank @Size(max = 128) String signature
) {
}
