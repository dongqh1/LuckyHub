package com.dongqh.luckyhub.membership.controller;

import com.dongqh.luckyhub.auth.context.LoginContext;
import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.membership.dto.PurchaseMembershipCommand;
import com.dongqh.luckyhub.membership.dto.PurchaseMyMembershipCommand;
import com.dongqh.luckyhub.membership.service.MembershipService;
import com.dongqh.luckyhub.membership.vo.UserMembershipView;
import com.dongqh.luckyhub.rbac.annotation.RequirePermission;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/memberships")
@RequirePermission(PermissionCodes.MEMBERSHIP_READ)
public class MembershipController {
    private final MembershipService service;
    public MembershipController(MembershipService service) { this.service = service; }

    @GetMapping("/me")
    public ApiResponse<UserMembershipView> mine() {
        return ApiResponse.success(service.getMine(LoginContext.require().userId()).orElse(null));
    }

    @PostMapping("/purchases")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserMembershipView> purchase(@Valid @RequestBody PurchaseMyMembershipCommand command) {
        long userId = LoginContext.require().userId();
        return ApiResponse.success(service.purchase(new PurchaseMembershipCommand(
                command.businessNo(), command.membershipProductId(), userId)));
    }
}
