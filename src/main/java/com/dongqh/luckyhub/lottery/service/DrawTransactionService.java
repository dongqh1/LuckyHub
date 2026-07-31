package com.dongqh.luckyhub.lottery.service;

import com.dongqh.luckyhub.lottery.model.DrawExecutionContext;
import com.dongqh.luckyhub.lottery.model.DrawExecutionResult;

public interface DrawTransactionService {

    DrawExecutionResult execute(DrawExecutionContext context);
}
