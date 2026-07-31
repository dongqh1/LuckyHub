package com.dongqh.luckyhub.lottery.algorithm;

public record DrawCandidate(Type type, Long activityPrizeId, Long prizeId) {

    public enum Type {
        PRIZE_CANDIDATE,
        NO_WIN
    }

    public static DrawCandidate prize(long activityPrizeId, long prizeId) {
        return new DrawCandidate(Type.PRIZE_CANDIDATE, activityPrizeId, prizeId);
    }

    public static DrawCandidate noWin() {
        return new DrawCandidate(Type.NO_WIN, null, null);
    }
}
