package com.dongqh.luckyhub.payment.vo;

import com.dongqh.luckyhub.payment.enums.PaymentResult;
import com.dongqh.luckyhub.payment.enums.PaymentStatus;

import java.time.LocalDateTime;

public record PaymentView(
        Long id,
        String paymentNo,
        String orderNo,
        Long userId,
        Long amountCent,
        PaymentStatus status,
        PaymentResult callbackResult,
        String failureReason,
        LocalDateTime paidAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
