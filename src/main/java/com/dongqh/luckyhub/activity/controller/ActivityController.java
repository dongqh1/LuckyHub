package com.dongqh.luckyhub.activity.controller;

import com.dongqh.luckyhub.activity.dto.ActivityQuery;
import com.dongqh.luckyhub.activity.dto.CreateActivityCommand;
import com.dongqh.luckyhub.activity.dto.UpdateActivityCommand;
import com.dongqh.luckyhub.activity.service.ActivityService;
import com.dongqh.luckyhub.activity.vo.ActivityView;
import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.rbac.annotation.RequirePermission;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/activities")
@Tag(name = "活动管理", description = "活动创建、查询、修改、发布、禁用和恢复接口")
public class ActivityController {

    private final ActivityService service;

    public ActivityController(ActivityService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "创建活动")
    @RequirePermission(PermissionCodes.ACTIVITY_CREATE)
    public ApiResponse<ActivityView> create(@Valid @RequestBody CreateActivityCommand command) {
        return ApiResponse.success(service.create(command));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询活动详情")
    @RequirePermission(PermissionCodes.ACTIVITY_READ)
    public ApiResponse<ActivityView> getById(@PathVariable long id) {
        return ApiResponse.success(service.getById(id));
    }

    @GetMapping
    @Operation(summary = "分页查询活动")
    @RequirePermission(PermissionCodes.ACTIVITY_READ)
    public ApiResponse<PageResponse<ActivityView>> page(@Valid @ModelAttribute ActivityQuery query) {
        return ApiResponse.success(service.page(query));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改活动")
    @RequirePermission(PermissionCodes.ACTIVITY_UPDATE)
    public ApiResponse<ActivityView> update(
            @PathVariable long id,
            @Valid @RequestBody UpdateActivityCommand command
    ) {
        return ApiResponse.success(service.update(id, command));
    }

    @PatchMapping("/{id}/publish")
    @Operation(summary = "发布活动")
    @RequirePermission(PermissionCodes.ACTIVITY_PUBLISH)
    public ApiResponse<ActivityView> publish(@PathVariable long id) {
        return ApiResponse.success(service.publish(id));
    }

    @PatchMapping("/{id}/disable")
    @Operation(summary = "禁用活动")
    @RequirePermission(PermissionCodes.ACTIVITY_DISABLE)
    public ApiResponse<Void> disable(@PathVariable long id) {
        service.disable(id);
        return ApiResponse.success();
    }

    @PatchMapping("/{id}/restore")
    @Operation(summary = "恢复活动为草稿")
    @RequirePermission(PermissionCodes.ACTIVITY_RESTORE)
    public ApiResponse<ActivityView> restore(@PathVariable long id) {
        return ApiResponse.success(service.restore(id));
    }
}
