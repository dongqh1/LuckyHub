package com.dongqh.luckyhub.lottery.service;

import com.dongqh.luckyhub.lottery.model.ReconciliationResult;

import java.time.Instant;

public interface LotteryReconciliationService {

    ReconciliationResult reconcileExpiredReservations(Instant now);
}
