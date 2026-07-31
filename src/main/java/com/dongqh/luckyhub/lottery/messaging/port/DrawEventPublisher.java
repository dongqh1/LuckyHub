package com.dongqh.luckyhub.lottery.messaging.port;

import com.dongqh.luckyhub.lottery.messaging.event.DrawEventEnvelope;

public interface DrawEventPublisher {

    void publish(DrawEventEnvelope event);
}
