package com.dongqh.luckyhub.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.coupon.service.CouponService;
import com.dongqh.luckyhub.inventory.channel.service.ChannelInventoryService;
import com.dongqh.luckyhub.order.entity.MallOrder;
import com.dongqh.luckyhub.order.enums.CashOrderStatus;
import com.dongqh.luckyhub.order.mapper.MallOrderMapper;
import com.dongqh.luckyhub.payment.dto.CreatePaymentCommand;
import com.dongqh.luckyhub.payment.dto.PaymentCallbackCommand;
import com.dongqh.luckyhub.payment.entity.PaymentOrder;
import com.dongqh.luckyhub.payment.enums.PaymentErrorCode;
import com.dongqh.luckyhub.payment.enums.PaymentResult;
import com.dongqh.luckyhub.payment.enums.PaymentStatus;
import com.dongqh.luckyhub.payment.mapper.PaymentOrderMapper;
import com.dongqh.luckyhub.payment.service.PaymentService;
import com.dongqh.luckyhub.payment.vo.PaymentView;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final String SIMULATION_SECRET = "local-simulation-only";

    private final PaymentOrderMapper paymentMapper;
    private final MallOrderMapper orderMapper;
    private final ChannelInventoryService inventory;
    private final CouponService coupons;

    public PaymentServiceImpl(PaymentOrderMapper paymentMapper, MallOrderMapper orderMapper,
                              ChannelInventoryService inventory, CouponService coupons) {
        this.paymentMapper = paymentMapper;
        this.orderMapper = orderMapper;
        this.inventory = inventory;
        this.coupons = coupons;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentView create(long userId, CreatePaymentCommand command) {
        String paymentNo = command.paymentNo().trim();
        String orderNo = command.orderNo().trim();
        PaymentOrder existing = find(paymentNo);
        if (existing != null) {
            return existing(existing, userId, orderNo);
        }

        MallOrder order = orderMapper.lockByOrderNo(orderNo);
        if (order == null || !Objects.equals(order.getUserId(), userId)
                || order.getStatus() != CashOrderStatus.PENDING_PAYMENT) {
            throw error(PaymentErrorCode.ORDER_NOT_PAYABLE);
        }
        PaymentOrder payment = new PaymentOrder();
        payment.setPaymentNo(paymentNo);
        payment.setOrderNo(orderNo);
        payment.setUserId(userId);
        payment.setAmountCent(order.getPayableAmountCent());
        payment.setStatus(PaymentStatus.PENDING);
        try {
            paymentMapper.insert(payment);
        } catch (DuplicateKeyException exception) {
            return existing(find(paymentNo), userId, orderNo);
        }
        return view(payment);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentView callback(PaymentCallbackCommand command) {
        String paymentNo = command.paymentNo().trim();
        PaymentOrder payment = paymentMapper.lockByPaymentNo(paymentNo);
        if (payment == null) {
            throw error(PaymentErrorCode.PAYMENT_NOT_FOUND);
        }
        verifySignature(payment, command);
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            if (command.result() != PaymentResult.SUCCESS) {
                throw error(PaymentErrorCode.PAYMENT_STATE_CONFLICT);
            }
            return view(payment);
        }
        if (payment.getStatus() == PaymentStatus.FAILED) {
            if (command.result() != PaymentResult.FAILURE) {
                throw error(PaymentErrorCode.PAYMENT_STATE_CONFLICT);
            }
            return view(payment);
        }

        if (command.result() == PaymentResult.PROCESSING) {
            payment.setCallbackResult(PaymentResult.PROCESSING);
            paymentMapper.updateById(payment);
            return view(payment);
        }
        if (command.result() == PaymentResult.FAILURE) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setCallbackResult(PaymentResult.FAILURE);
            payment.setFailureReason(command.failureReason());
            paymentMapper.updateById(payment);
            return view(payment);
        }

        MallOrder order = orderMapper.lockByOrderNo(payment.getOrderNo());
        if (order == null || order.getStatus() != CashOrderStatus.PENDING_PAYMENT
                || !Objects.equals(order.getUserId(), payment.getUserId())) {
            throw error(PaymentErrorCode.ORDER_NOT_PAYABLE);
        }
        if (!Objects.equals(order.getPayableAmountCent(), payment.getAmountCent())) {
            throw error(PaymentErrorCode.AMOUNT_MISMATCH);
        }
        inventory.confirm(order.getOrderNo());
        if (order.getUserCouponId() != null) {
            coupons.useForOrder(order.getUserCouponId(), order.getOrderNo());
        }
        if (orderMapper.markPaid(order.getOrderNo()) != 1) {
            throw error(PaymentErrorCode.ORDER_NOT_PAYABLE);
        }
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setCallbackResult(PaymentResult.SUCCESS);
        payment.setFailureReason(null);
        payment.setPaidAt(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
        paymentMapper.updateById(payment);
        return view(payment);
    }

    @Override
    public String signForSimulation(String paymentNo, PaymentResult result, long amountCent) {
        String payload = paymentNo.trim() + "|" + result.name() + "|" + amountCent + "|" + SIMULATION_SECRET;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    @Override
    public PaymentView simulate(String paymentNo, PaymentResult result, String failureReason) {
        PaymentOrder payment = find(paymentNo.trim());
        if (payment == null) {
            throw error(PaymentErrorCode.PAYMENT_NOT_FOUND);
        }
        String signature = signForSimulation(payment.getPaymentNo(), result, payment.getAmountCent());
        return callback(new PaymentCallbackCommand(payment.getPaymentNo(), result, failureReason, signature));
    }

    private void verifySignature(PaymentOrder payment, PaymentCallbackCommand command) {
        byte[] expected = signForSimulation(payment.getPaymentNo(), command.result(), payment.getAmountCent())
                .getBytes(StandardCharsets.UTF_8);
        byte[] actual = command.signature().getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw error(PaymentErrorCode.SIGNATURE_INVALID);
        }
    }

    private PaymentView existing(PaymentOrder payment, long userId, String orderNo) {
        if (payment == null || !Objects.equals(payment.getUserId(), userId)
                || !Objects.equals(payment.getOrderNo(), orderNo)) {
            throw error(PaymentErrorCode.PAYMENT_DUPLICATE);
        }
        return view(payment);
    }

    private PaymentOrder find(String paymentNo) {
        return paymentMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getPaymentNo, paymentNo));
    }

    private PaymentView view(PaymentOrder payment) {
        return new PaymentView(payment.getId(), payment.getPaymentNo(), payment.getOrderNo(),
                payment.getUserId(), payment.getAmountCent(), payment.getStatus(),
                payment.getCallbackResult(), payment.getFailureReason(), payment.getPaidAt(),
                payment.getCreatedAt(), payment.getUpdatedAt());
    }

    private BusinessException error(PaymentErrorCode errorCode) {
        return new BusinessException(errorCode);
    }
}
