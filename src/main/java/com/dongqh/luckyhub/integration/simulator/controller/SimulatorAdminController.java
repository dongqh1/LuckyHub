package com.dongqh.luckyhub.integration.simulator.controller;

import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.integration.simulator.ConfigureSimulatorFailureRuleCommand;
import com.dongqh.luckyhub.integration.simulator.SimulatorFailureRuleService;
import com.dongqh.luckyhub.rbac.annotation.RequirePermission;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import com.dongqh.luckyhub.shipping.crypto.LogisticsCallbackSigner;
import com.dongqh.luckyhub.shipping.dto.LogisticsCallbackCommand;
import com.dongqh.luckyhub.shipping.dto.SimulateTrackingEventCommand;
import com.dongqh.luckyhub.shipping.entity.ShippingOrder;
import com.dongqh.luckyhub.shipping.enums.ShippingErrorCode;
import com.dongqh.luckyhub.shipping.mapper.ShippingOrderMapper;
import com.dongqh.luckyhub.shipping.service.LogisticsCallbackService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/admin/simulators")
public class SimulatorAdminController {
    private final SimulatorFailureRuleService service;
    private final ShippingOrderMapper shippingOrders;
    private final LogisticsCallbackSigner signer;
    private final LogisticsCallbackService callbacks;

    public SimulatorAdminController(
            SimulatorFailureRuleService service,
            ShippingOrderMapper shippingOrders,
            LogisticsCallbackSigner signer,
            LogisticsCallbackService callbacks
    ) {
        this.service = service;
        this.shippingOrders = shippingOrders;
        this.signer = signer;
        this.callbacks = callbacks;
    }

    @PostMapping("/failure-rules")
    @RequirePermission(PermissionCodes.SIMULATOR_CONTROL)
    public ApiResponse<Void> configure(@Valid @RequestBody ConfigureSimulatorFailureRuleCommand command) {
        service.configure(command.fulfillmentType(), command.failureMode(), command.count());
        return ApiResponse.success();
    }

    @PostMapping("/logistics/{fulfillmentNo}/events")
    @RequirePermission(PermissionCodes.SIMULATOR_CONTROL)
    public ApiResponse<Void> trackingEvent(
            @PathVariable @Size(max = 64) String fulfillmentNo,
            @Valid @RequestBody SimulateTrackingEventCommand command
    ) {
        ShippingOrder order = shippingOrders.selectByFulfillmentNo(fulfillmentNo.trim());
        if (order == null || order.getWaybillNo() == null || order.getWaybillNo().isBlank()) {
            throw new BusinessException(ShippingErrorCode.SHIPPING_NOT_FOUND);
        }
        LogisticsCallbackCommand unsigned = new LogisticsCallbackCommand(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                Instant.now().getEpochSecond(), order.getWaybillNo(), command.eventType(),
                command.eventTime(), command.locationSummary(), command.description(), null);
        LogisticsCallbackCommand signed = new LogisticsCallbackCommand(
                unsigned.callbackId(), unsigned.nonce(), unsigned.timestampEpochSecond(),
                unsigned.waybillNo(), unsigned.eventType(), unsigned.eventTime(),
                unsigned.locationSummary(), unsigned.description(), signer.sign(unsigned));
        callbacks.handle(signed);
        return ApiResponse.success();
    }
}
