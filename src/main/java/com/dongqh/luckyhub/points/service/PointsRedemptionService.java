package com.dongqh.luckyhub.points.service;

import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.points.dto.CreatePointsRedemptionCommand;
import com.dongqh.luckyhub.points.dto.PointsRedemptionQuery;
import com.dongqh.luckyhub.points.dto.ReversePointsRedemptionCommand;
import com.dongqh.luckyhub.points.vo.PointsRedemptionView;

public interface PointsRedemptionService {
    PointsRedemptionView create(long userId, CreatePointsRedemptionCommand command);

    PointsRedemptionView get(long userId, String redemptionNo);

    PageResponse<PointsRedemptionView> page(long userId, PointsRedemptionQuery query);

    PointsRedemptionView reverse(String redemptionNo, ReversePointsRedemptionCommand command);
}
