package com.dongqh.luckyhub.membership.controller;

import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.membership.dto.CreateMembershipProductCommand;
import com.dongqh.luckyhub.membership.service.MembershipService;
import com.dongqh.luckyhub.membership.vo.MembershipProductView;
import com.dongqh.luckyhub.rbac.annotation.RequirePermission;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/membership-products")
public class MembershipAdminController {
    private final MembershipService service;
    public MembershipAdminController(MembershipService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCodes.MEMBERSHIP_MANAGE)
    public ApiResponse<MembershipProductView> create(@Valid @RequestBody CreateMembershipProductCommand command) {
        return ApiResponse.success(service.createProduct(command));
    }
}
