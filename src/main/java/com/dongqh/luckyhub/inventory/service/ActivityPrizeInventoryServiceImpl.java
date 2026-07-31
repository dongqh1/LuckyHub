package com.dongqh.luckyhub.inventory.service;

import com.dongqh.luckyhub.inventory.mapper.ActivityPrizeInventoryMapper;
import org.springframework.stereotype.Service;

@Service
public class ActivityPrizeInventoryServiceImpl implements ActivityPrizeInventoryService {

    private final ActivityPrizeInventoryMapper inventoryMapper;

    public ActivityPrizeInventoryServiceImpl(ActivityPrizeInventoryMapper inventoryMapper) {
        this.inventoryMapper = inventoryMapper;
    }

    @Override
    public boolean decrementIfAvailable(long activityPrizeId) {
        return inventoryMapper.decrementIfAvailable(activityPrizeId) == 1;
    }
}
