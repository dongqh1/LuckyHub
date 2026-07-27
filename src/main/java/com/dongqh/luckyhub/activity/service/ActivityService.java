package com.dongqh.luckyhub.activity.service;

import com.dongqh.luckyhub.activity.dto.ActivityQuery;
import com.dongqh.luckyhub.activity.dto.CreateActivityCommand;
import com.dongqh.luckyhub.activity.dto.UpdateActivityCommand;
import com.dongqh.luckyhub.activity.vo.ActivityView;
import com.dongqh.luckyhub.common.result.PageResponse;

public interface ActivityService {

    ActivityView create(CreateActivityCommand command);

    ActivityView getById(long id);

    PageResponse<ActivityView> page(ActivityQuery query);

    ActivityView update(long id, UpdateActivityCommand command);

    ActivityView publish(long id);

    void disable(long id);

    ActivityView restore(long id);
}
