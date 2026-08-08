package com.dongqh.luckyhub.inventory.channel.controller;

import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.inventory.channel.dto.AllocateChannelStockCommand;
import com.dongqh.luckyhub.inventory.channel.dto.InitializeSkuStockCommand;
import com.dongqh.luckyhub.inventory.channel.dto.ReserveChannelStockCommand;
import com.dongqh.luckyhub.inventory.channel.service.ChannelInventoryService;
import com.dongqh.luckyhub.inventory.channel.vo.ChannelInventoryView;
import com.dongqh.luckyhub.rbac.annotation.RequirePermission;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/inventory")
@RequirePermission(PermissionCodes.INVENTORY_MANAGE)
public class ChannelInventoryController {

    private final ChannelInventoryService service;

    public ChannelInventoryController(ChannelInventoryService service) {
        this.service = service;
    }

    @PostMapping("/skus/initialize")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChannelInventoryView> initialize(
            @Valid @RequestBody InitializeSkuStockCommand command
    ) {
        return ApiResponse.success(service.initialize(command));
    }

    @PostMapping("/channels/allocate")
    public ApiResponse<ChannelInventoryView> allocate(
            @Valid @RequestBody AllocateChannelStockCommand command
    ) {
        return ApiResponse.success(service.allocate(command));
    }

    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChannelInventoryView> reserve(
            @Valid @RequestBody ReserveChannelStockCommand command
    ) {
        return ApiResponse.success(service.reserve(command));
    }

    @PostMapping("/reservations/{reservationNo}/confirm")
    public ApiResponse<ChannelInventoryView> confirm(
            @PathVariable @Size(max = 64) String reservationNo
    ) {
        return ApiResponse.success(service.confirm(reservationNo));
    }

    @PostMapping("/reservations/{reservationNo}/release")
    public ApiResponse<ChannelInventoryView> release(
            @PathVariable @Size(max = 64) String reservationNo
    ) {
        return ApiResponse.success(service.release(reservationNo));
    }

    @GetMapping("/skus/{skuId}/channels/{channelCode}")
    public ApiResponse<ChannelInventoryView> get(
            @PathVariable @Positive long skuId,
            @PathVariable @Size(max = 100) String channelCode
    ) {
        return ApiResponse.success(service.get(skuId, channelCode));
    }
}
