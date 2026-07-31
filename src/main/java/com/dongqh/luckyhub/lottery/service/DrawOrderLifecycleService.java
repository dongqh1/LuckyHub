package com.dongqh.luckyhub.lottery.service;

import com.dongqh.luckyhub.lottery.entity.LotteryDrawOrder;
import com.dongqh.luckyhub.lottery.model.NewDrawOrder;

import java.time.LocalDateTime;

public interface DrawOrderLifecycleService {

    LotteryDrawOrder createProcessing(NewDrawOrder command);

    void markFailed(long orderId, String safeReason);

    void markFailedAndRequestRelease(LotteryDrawOrder order, String safeReason, LocalDateTime occurredAt);
}
