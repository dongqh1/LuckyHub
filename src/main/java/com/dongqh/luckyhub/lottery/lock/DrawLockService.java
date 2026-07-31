package com.dongqh.luckyhub.lottery.lock;

import java.util.function.Supplier;

public interface DrawLockService {

    <T> T execute(long activityId, long userId, Supplier<T> action);
}
