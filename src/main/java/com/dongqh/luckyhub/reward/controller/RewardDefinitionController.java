package com.dongqh.luckyhub.reward.controller;

import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.rbac.annotation.RequirePermission;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import com.dongqh.luckyhub.reward.dto.CreateRewardDefinitionCommand;
import com.dongqh.luckyhub.reward.service.RewardDefinitionService;
import com.dongqh.luckyhub.reward.vo.RewardDefinitionView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/reward-definitions")
public class RewardDefinitionController {

    private final RewardDefinitionService service;

    public RewardDefinitionController(RewardDefinitionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCodes.REWARD_MANAGE)
    public ApiResponse<RewardDefinitionView> create(
            @Valid @RequestBody CreateRewardDefinitionCommand command
    ) {
        return ApiResponse.success(service.create(command));
    }

    @GetMapping("/{id}")
    @RequirePermission(PermissionCodes.REWARD_MANAGE)
    public ApiResponse<RewardDefinitionView> get(@PathVariable @Positive long id) {
        return ApiResponse.success(service.get(id));
    }
}
