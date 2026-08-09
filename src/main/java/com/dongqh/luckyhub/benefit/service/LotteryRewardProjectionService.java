package com.dongqh.luckyhub.benefit.service;

public interface LotteryRewardProjectionService {
    int projectBatch(int limit);

    void project(long benefitId);
}
