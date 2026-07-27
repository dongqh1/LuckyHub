package com.dongqh.luckyhub.activity.controller;

import com.dongqh.luckyhub.activity.dto.AddActivityPrizeCommand;
import com.dongqh.luckyhub.activity.dto.UpdateActivityPrizeCommand;
import com.dongqh.luckyhub.activity.service.ActivityPrizeService;
import com.dongqh.luckyhub.activity.vo.ActivityPrizeView;
import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.rbac.annotation.RequirePermission;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/activities/{activityId}/prizes")
@Tag(name = "活动奖品", description = "活动奖品、权重和库存配置接口")
public class ActivityPrizeController {

    private final ActivityPrizeService service;

    public ActivityPrizeController(ActivityPrizeService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "添加活动奖品")
    @RequirePermission(PermissionCodes.ACTIVITY_PRIZE_MANAGE)
    public ApiResponse<ActivityPrizeView> add(
            @PathVariable long activityId,
            @Valid @RequestBody AddActivityPrizeCommand command
    ) {
        return ApiResponse.success(service.add(activityId, command));
    }

    @GetMapping
    @Operation(summary = "查询活动奖品")
    @RequirePermission(PermissionCodes.ACTIVITY_READ)
    public ApiResponse<List<ActivityPrizeView>> list(@PathVariable long activityId) {
        return ApiResponse.success(service.list(activityId));
    }

    @PutMapping("/{prizeId}")
    @Operation(summary = "修改活动奖品")
    @RequirePermission(PermissionCodes.ACTIVITY_PRIZE_MANAGE)
    public ApiResponse<ActivityPrizeView> update(
            @PathVariable long activityId,
            @PathVariable long prizeId,
            @Valid @RequestBody UpdateActivityPrizeCommand command
    ) {
        return ApiResponse.success(service.update(activityId, prizeId, command));
    }

    @DeleteMapping("/{prizeId}")
    @Operation(summary = "移除活动奖品")
    @RequirePermission(PermissionCodes.ACTIVITY_PRIZE_MANAGE)
    public ApiResponse<Void> remove(
            @PathVariable long activityId,
            @PathVariable long prizeId
    ) {
        service.remove(activityId, prizeId);
        return ApiResponse.success();
    }
}
