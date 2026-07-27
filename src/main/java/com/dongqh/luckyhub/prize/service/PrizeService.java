package com.dongqh.luckyhub.prize.service;

import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.prize.dto.CreatePrizeCommand;
import com.dongqh.luckyhub.prize.dto.PrizeQuery;
import com.dongqh.luckyhub.prize.dto.UpdatePrizeCommand;
import com.dongqh.luckyhub.prize.vo.PrizeView;

public interface PrizeService {

    PrizeView create(CreatePrizeCommand command);

    PrizeView getById(long id);

    PageResponse<PrizeView> page(PrizeQuery query);

    PrizeView update(long id, UpdatePrizeCommand command);

    void disable(long id);
}
