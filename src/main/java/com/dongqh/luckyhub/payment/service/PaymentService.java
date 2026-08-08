package com.dongqh.luckyhub.payment.service;

import com.dongqh.luckyhub.payment.dto.CreatePaymentCommand;
import com.dongqh.luckyhub.payment.dto.PaymentCallbackCommand;
import com.dongqh.luckyhub.payment.enums.PaymentResult;
import com.dongqh.luckyhub.payment.vo.PaymentView;

public interface PaymentService {
    PaymentView create(long userId, CreatePaymentCommand command);

    PaymentView callback(PaymentCallbackCommand command);

    String signForSimulation(String paymentNo, PaymentResult result, long amountCent);

    PaymentView simulate(String paymentNo, PaymentResult result, String failureReason);
}
