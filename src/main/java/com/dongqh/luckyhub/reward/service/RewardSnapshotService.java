package com.dongqh.luckyhub.reward.service;

import com.dongqh.luckyhub.prize.entity.MarketingPrize;
import com.dongqh.luckyhub.reward.model.RewardSnapshot;
import java.util.List;
import java.util.Map;

public interface RewardSnapshotService {
    Map<Long, RewardSnapshot> resolveForPrizes(List<MarketingPrize> prizes);
}
