package com.dongqh.luckyhub.inventory.channel.vo;

import com.dongqh.luckyhub.inventory.channel.enums.InventoryReservationStatus;

public record ChannelInventoryView(
        Long skuId,
        String channelCode,
        Integer totalStock,
        Integer allocatedStock,
        Integer availableStock,
        Integer reservedStock,
        Integer consumedStock,
        String reservationNo,
        InventoryReservationStatus reservationStatus
) {
}
