package com.dongqh.luckyhub.lottery.algorithm;

import java.util.List;

public interface WeightedDrawEngine {

    DrawCandidate select(List<PrizeWeightSnapshot> prizes, int noWinWeight);
}
