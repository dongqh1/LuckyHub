package com.dongqh.luckyhub.lottery.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dongqh.luckyhub.activity.entity.MarketingActivity;
import com.dongqh.luckyhub.activity.mapper.MarketingActivityMapper;
import com.dongqh.luckyhub.benefit.entity.UserBenefit;
import com.dongqh.luckyhub.benefit.mapper.UserBenefitMapper;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.lottery.dto.DrawOrderQuery;
import com.dongqh.luckyhub.lottery.dto.DrawRecordQuery;
import com.dongqh.luckyhub.lottery.entity.LotteryDrawOrder;
import com.dongqh.luckyhub.lottery.entity.LotteryDrawRecord;
import com.dongqh.luckyhub.lottery.enums.LotteryErrorCode;
import com.dongqh.luckyhub.lottery.mapper.LotteryDrawOrderMapper;
import com.dongqh.luckyhub.lottery.mapper.LotteryDrawRecordMapper;
import com.dongqh.luckyhub.lottery.service.LotteryQueryService;
import com.dongqh.luckyhub.lottery.vo.*;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import com.dongqh.luckyhub.rbac.service.DataScopeService;
import com.dongqh.luckyhub.rbac.service.UserDataScope;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class LotteryQueryServiceImpl implements LotteryQueryService {
    private final MarketingActivityMapper activityMapper;
    private final LotteryDrawOrderMapper orderMapper;
    private final LotteryDrawRecordMapper recordMapper;
    private final UserBenefitMapper benefitMapper;
    private final DataScopeService dataScopeService;

    public LotteryQueryServiceImpl(MarketingActivityMapper activityMapper, LotteryDrawOrderMapper orderMapper,
                                   LotteryDrawRecordMapper recordMapper, UserBenefitMapper benefitMapper,
                                   DataScopeService dataScopeService) {
        this.activityMapper = activityMapper; this.orderMapper = orderMapper; this.recordMapper = recordMapper;
        this.benefitMapper = benefitMapper; this.dataScopeService = dataScopeService;
    }

    @Override
    public LotteryActivityView getActivity(long activityId) {
        MarketingActivity activity = activityMapper.selectById(activityId);
        if (activity == null) throw new BusinessException(LotteryErrorCode.ACTIVITY_NOT_FOUND);
        return new LotteryActivityView(activity.getId(), activity.getActivityName(), activity.getDescription(),
                activity.getStatus(), activity.getStartTime(), activity.getEndTime(), activity.getDailyLimit());
    }

    @Override
    public DrawOrderView getDraw(String requestId) {
        LotteryDrawOrder order = orderMapper.selectByRequestId(requestId);
        if (order == null) throw new BusinessException(LotteryErrorCode.DRAW_ACCESS_DENIED, "抽奖订单不存在或无权访问");
        dataScopeService.resolveUserScope(order.getUserId(), PermissionCodes.LOTTERY_DRAW_READ_ALL);
        return toOrderView(order, recordMapper.selectByOrderId(order.getId()));
    }

    @Override
    public PageResponse<DrawOrderView> pageOrders(DrawOrderQuery query) {
        UserDataScope scope = dataScopeService.resolveUserScope(query.getUserId(), PermissionCodes.LOTTERY_ORDER_READ_ALL);
        LambdaQueryWrapper<LotteryDrawOrder> wrapper = new LambdaQueryWrapper<LotteryDrawOrder>()
                .eq(!scope.all(), LotteryDrawOrder::getUserId, scope.userId())
                .eq(query.getActivityId() != null, LotteryDrawOrder::getActivityId, query.getActivityId())
                .eq(query.getStatus() != null, LotteryDrawOrder::getStatus, query.getStatus())
                .eq(query.getDrawDate() != null, LotteryDrawOrder::getDrawDate, query.getDrawDate())
                .orderByDesc(LotteryDrawOrder::getCreatedAt).orderByDesc(LotteryDrawOrder::getId);
        Page<LotteryDrawOrder> page = orderMapper.selectPage(Page.of(query.getPage(), query.getSize()), wrapper);
        Map<Long, List<LotteryDrawRecord>> records = page.getRecords().isEmpty() ? Map.of()
                : recordMapper.selectByOrderIds(page.getRecords().stream().map(LotteryDrawOrder::getId).toList())
                .stream().collect(Collectors.groupingBy(LotteryDrawRecord::getOrderId));
        List<DrawOrderView> views = page.getRecords().stream()
                .map(order -> toOrderView(order, records.getOrDefault(order.getId(), List.of()))).toList();
        return new PageResponse<>(views, page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    @Override
    public PageResponse<DrawRecordView> pageRecords(DrawRecordQuery query) {
        UserDataScope scope = dataScopeService.resolveUserScope(query.getUserId(), PermissionCodes.LOTTERY_RECORD_READ_ALL);
        LambdaQueryWrapper<LotteryDrawRecord> wrapper = new LambdaQueryWrapper<LotteryDrawRecord>()
                .eq(!scope.all(), LotteryDrawRecord::getUserId, scope.userId())
                .eq(query.getActivityId() != null, LotteryDrawRecord::getActivityId, query.getActivityId())
                .eq(query.getResultType() != null, LotteryDrawRecord::getResultType, query.getResultType())
                .ge(query.getStartDate() != null, LotteryDrawRecord::getDrawTime,
                        query.getStartDate() == null ? null : query.getStartDate().atStartOfDay())
                .lt(query.getEndDate() != null, LotteryDrawRecord::getDrawTime,
                        query.getEndDate() == null ? null : query.getEndDate().plusDays(1).atStartOfDay())
                .orderByDesc(LotteryDrawRecord::getDrawTime).orderByDesc(LotteryDrawRecord::getId);
        Page<LotteryDrawRecord> page = recordMapper.selectPage(Page.of(query.getPage(), query.getSize()), wrapper);
        Map<Long, Long> benefits = benefitIds(page.getRecords());
        List<DrawRecordView> views = page.getRecords().stream().map(record -> toRecordView(record, benefits.get(record.getId()))).toList();
        return new PageResponse<>(views, page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    private Map<Long, Long> benefitIds(List<LotteryDrawRecord> records) {
        if (records.isEmpty()) return Map.of();
        List<Long> ids = records.stream().map(LotteryDrawRecord::getId).toList();
        return benefitMapper.selectList(new LambdaQueryWrapper<UserBenefit>().in(UserBenefit::getDrawRecordId, ids))
                .stream().collect(Collectors.toMap(UserBenefit::getDrawRecordId, UserBenefit::getId));
    }

    private DrawOrderView toOrderView(LotteryDrawOrder order, List<LotteryDrawRecord> records) {
        List<DrawResultView> results = records.stream().map(record -> new DrawResultView(record.getId(), record.getSequenceNo(),
                record.getResultType(), record.getPrizeId(), record.getPrizeName(), record.getPrizeType(),
                record.getPrizeImageUrl(), record.getBenefitId())).toList();
        return new DrawOrderView(order.getId(), order.getRequestId(), order.getActivityId(), order.getDrawCount(),
                order.getDrawDate(), order.getStatus(), order.getFailReason(), order.getCompletedAt(), results);
    }

    private DrawRecordView toRecordView(LotteryDrawRecord record, Long benefitId) {
        return new DrawRecordView(record.getId(), record.getOrderId(), record.getRequestId(), record.getSequenceNo(),
                record.getUserId(), record.getActivityId(), record.getResultType(), record.getPrizeId(), record.getPrizeName(),
                record.getPrizeType(), record.getPrizeImageUrl(), benefitId, record.getDrawTime());
    }
}
