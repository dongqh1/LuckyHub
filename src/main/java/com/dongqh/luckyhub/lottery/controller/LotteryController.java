package com.dongqh.luckyhub.lottery.controller;

import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.lottery.dto.DrawCommand;
import com.dongqh.luckyhub.lottery.dto.DrawOrderQuery;
import com.dongqh.luckyhub.lottery.dto.DrawRecordQuery;
import com.dongqh.luckyhub.lottery.service.LotteryQueryService;
import com.dongqh.luckyhub.lottery.service.LotteryService;
import com.dongqh.luckyhub.lottery.vo.DrawOrderView;
import com.dongqh.luckyhub.lottery.vo.DrawRecordView;
import com.dongqh.luckyhub.lottery.vo.LotteryActivityView;
import com.dongqh.luckyhub.rbac.annotation.RequirePermission;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated @RestController @RequestMapping("/api/lottery")
@Tag(name = "抽奖", description = "活动参与、抽奖结果和审计查询接口")
public class LotteryController {
    private final LotteryService lotteryService;
    private final LotteryQueryService queryService;
    public LotteryController(LotteryService lotteryService, LotteryQueryService queryService) {
        this.lotteryService = lotteryService; this.queryService = queryService;
    }
    @GetMapping("/activities/{activityId}") @Operation(summary = "查询可参与活动详情")
    @RequirePermission(PermissionCodes.LOTTERY_ACTIVITY_READ)
    public ApiResponse<LotteryActivityView> getActivity(@PathVariable @Positive long activityId) { return ApiResponse.success(queryService.getActivity(activityId)); }
    @PostMapping("/draws") @Operation(summary = "单抽或十连抽")
    @RequirePermission(PermissionCodes.LOTTERY_DRAW)
    public ApiResponse<DrawOrderView> draw(@Valid @RequestBody DrawCommand command) { return ApiResponse.success(lotteryService.draw(command)); }
    @GetMapping("/draws/{requestId}") @Operation(summary = "按请求ID查询抽奖结果")
    @RequirePermission(PermissionCodes.LOTTERY_DRAW_READ)
    public ApiResponse<DrawOrderView> getDraw(@PathVariable @NotBlank @Size(max = 64) String requestId) { return ApiResponse.success(queryService.getDraw(requestId)); }
    @GetMapping("/orders") @Operation(summary = "分页查询全部抽奖订单")
    @RequirePermission(PermissionCodes.LOTTERY_ORDER_READ_ALL)
    public ApiResponse<PageResponse<DrawOrderView>> pageOrders(@Valid @ModelAttribute DrawOrderQuery query) { return ApiResponse.success(queryService.pageOrders(query)); }
    @GetMapping("/records") @Operation(summary = "分页查询抽奖记录")
    @RequirePermission(PermissionCodes.LOTTERY_RECORD_READ)
    public ApiResponse<PageResponse<DrawRecordView>> pageRecords(@Valid @ModelAttribute DrawRecordQuery query) { return ApiResponse.success(queryService.pageRecords(query)); }
}
