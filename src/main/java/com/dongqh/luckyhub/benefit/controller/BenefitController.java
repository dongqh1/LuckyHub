package com.dongqh.luckyhub.benefit.controller;
import com.dongqh.luckyhub.auth.context.LoginContext;
import com.dongqh.luckyhub.benefit.dto.BenefitQuery; import com.dongqh.luckyhub.benefit.service.BenefitQueryService; import com.dongqh.luckyhub.benefit.vo.BenefitView;
import com.dongqh.luckyhub.shipping.dto.ClaimPhysicalBenefitCommand; import com.dongqh.luckyhub.shipping.service.PhysicalClaimService; import com.dongqh.luckyhub.shipping.vo.ShippingOrderView;
import com.dongqh.luckyhub.common.result.*; import com.dongqh.luckyhub.rbac.annotation.RequirePermission; import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import io.swagger.v3.oas.annotations.Operation; import io.swagger.v3.oas.annotations.tags.Tag; import jakarta.validation.Valid; import jakarta.validation.constraints.Positive; import org.springframework.validation.annotation.Validated; import org.springframework.web.bind.annotation.*;
@Validated @RestController @RequestMapping("/api/benefits") @Tag(name="用户权益",description="用户权益和管理查询接口")
public class BenefitController { private final BenefitQueryService service; private final PhysicalClaimService claims; public BenefitController(BenefitQueryService service, PhysicalClaimService claims){this.service=service;this.claims=claims;}
 @GetMapping @Operation(summary="分页查询权益") @RequirePermission(PermissionCodes.BENEFIT_READ) public ApiResponse<PageResponse<BenefitView>> page(@Valid @ModelAttribute BenefitQuery query){return ApiResponse.success(service.page(query));}
 @GetMapping("/{id}") @Operation(summary="查询权益详情") @RequirePermission(PermissionCodes.BENEFIT_READ) public ApiResponse<BenefitView> getById(@PathVariable @Positive long id){return ApiResponse.success(service.getById(id));}
 @PostMapping("/{id}/claim") @Operation(summary="领取抽奖实物权益") @RequirePermission(PermissionCodes.BENEFIT_READ) public ApiResponse<ShippingOrderView> claim(@PathVariable @Positive long id,@Valid @RequestBody ClaimPhysicalBenefitCommand command){return ApiResponse.success(claims.claim(LoginContext.require().userId(),id,command));}
}
