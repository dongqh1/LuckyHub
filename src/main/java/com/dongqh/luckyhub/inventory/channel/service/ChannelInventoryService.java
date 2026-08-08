package com.dongqh.luckyhub.inventory.channel.service;

import com.dongqh.luckyhub.inventory.channel.dto.AllocateChannelStockCommand;
import com.dongqh.luckyhub.inventory.channel.dto.InitializeSkuStockCommand;
import com.dongqh.luckyhub.inventory.channel.dto.ReserveChannelStockCommand;
import com.dongqh.luckyhub.inventory.channel.vo.ChannelInventoryView;

public interface ChannelInventoryService {
    ChannelInventoryView initialize(InitializeSkuStockCommand command);
    ChannelInventoryView allocate(AllocateChannelStockCommand command);
    ChannelInventoryView reserve(ReserveChannelStockCommand command);
    ChannelInventoryView confirm(String reservationNo);
    ChannelInventoryView release(String reservationNo);
    ChannelInventoryView reverseConfirmed(String reservationNo);
    ChannelInventoryView get(long skuId, String channelCode);
}
