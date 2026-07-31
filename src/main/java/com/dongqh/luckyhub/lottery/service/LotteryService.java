package com.dongqh.luckyhub.lottery.service;

import com.dongqh.luckyhub.lottery.dto.DrawCommand;
import com.dongqh.luckyhub.lottery.vo.DrawOrderView;

public interface LotteryService {
    DrawOrderView draw(DrawCommand command);
    DrawOrderView getByRequestId(String requestId);
}
