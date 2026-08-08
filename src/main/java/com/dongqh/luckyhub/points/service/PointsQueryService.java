package com.dongqh.luckyhub.points.service;

import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.points.dto.PointsLedgerQuery;
import com.dongqh.luckyhub.points.vo.PointsAccountView;
import com.dongqh.luckyhub.points.vo.PointsLedgerView;

public interface PointsQueryService {
    PointsAccountView getAccount(long userId);

    PageResponse<PointsLedgerView> pageLedgers(long userId, PointsLedgerQuery query);
}
