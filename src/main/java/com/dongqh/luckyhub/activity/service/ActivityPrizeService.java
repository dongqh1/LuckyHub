package com.dongqh.luckyhub.activity.service;

import com.dongqh.luckyhub.activity.dto.AddActivityPrizeCommand;
import com.dongqh.luckyhub.activity.dto.UpdateActivityPrizeCommand;
import com.dongqh.luckyhub.activity.vo.ActivityPrizeView;

import java.util.List;

public interface ActivityPrizeService {

    ActivityPrizeView add(long activityId, AddActivityPrizeCommand command);

    List<ActivityPrizeView> list(long activityId);

    ActivityPrizeView update(
            long activityId,
            long prizeId,
            UpdateActivityPrizeCommand command
    );

    void remove(long activityId, long prizeId);
}
