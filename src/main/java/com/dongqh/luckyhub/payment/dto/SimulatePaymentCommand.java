package com.dongqh.luckyhub.payment.dto;

import com.dongqh.luckyhub.payment.enums.PaymentResult;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SimulatePaymentCommand(@NotNull PaymentResult result, @Size(max = 255) String failureReason) {
}
