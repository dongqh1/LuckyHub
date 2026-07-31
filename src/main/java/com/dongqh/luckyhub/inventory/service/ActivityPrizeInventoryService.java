package com.dongqh.luckyhub.inventory.service;

public interface ActivityPrizeInventoryService {

    boolean decrementIfAvailable(long activityPrizeId);
}
