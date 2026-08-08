package com.dongqh.luckyhub.points.service;

import com.dongqh.luckyhub.points.dto.AdminPointsAdjustmentCommand;
import com.dongqh.luckyhub.points.dto.PointsMutationCommand;
import com.dongqh.luckyhub.points.dto.PointsReversalCommand;
import com.dongqh.luckyhub.points.vo.PointsAccountView;
import com.dongqh.luckyhub.points.vo.PointsLedgerView;

public interface PointsAccountService {
    PointsLedgerView credit(PointsMutationCommand command);

    PointsLedgerView debit(PointsMutationCommand command);

    PointsLedgerView reverseDebit(PointsReversalCommand command);

    PointsLedgerView adjust(AdminPointsAdjustmentCommand command);

    PointsAccountView get(long userId);
}
