package com.dongqh.luckyhub.drawchance.vo;

public record DrawChanceAccountView(long userId, long availableBalance, long reservedBalance) {
    public DrawChanceAccountView {
        if (userId <= 0 || availableBalance < 0 || reservedBalance < 0) {
            throw new IllegalArgumentException("抽奖次数账户不合法");
        }
    }
}
