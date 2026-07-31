package com.dongqh.luckyhub.lottery.service;

import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.lottery.dto.DrawOrderQuery;
import com.dongqh.luckyhub.lottery.dto.DrawRecordQuery;
import com.dongqh.luckyhub.lottery.vo.DrawOrderView;
import com.dongqh.luckyhub.lottery.vo.DrawRecordView;
import com.dongqh.luckyhub.lottery.vo.LotteryActivityView;

public interface LotteryQueryService {
    LotteryActivityView getActivity(long activityId);
    DrawOrderView getDraw(String requestId);
    PageResponse<DrawOrderView> pageOrders(DrawOrderQuery query);
    PageResponse<DrawRecordView> pageRecords(DrawRecordQuery query);
}
