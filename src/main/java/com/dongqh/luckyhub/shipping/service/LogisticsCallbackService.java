package com.dongqh.luckyhub.shipping.service;

import com.dongqh.luckyhub.shipping.dto.LogisticsCallbackCommand;

public interface LogisticsCallbackService {
    void handle(LogisticsCallbackCommand command);
}
