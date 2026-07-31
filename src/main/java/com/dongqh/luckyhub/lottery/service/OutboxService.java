package com.dongqh.luckyhub.lottery.service;

import com.dongqh.luckyhub.lottery.messaging.event.DrawEventEnvelope;

public interface OutboxService {

    void append(DrawEventEnvelope event);
}
