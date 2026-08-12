package com.dongqh.luckyhub.benefit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dongqh.luckyhub.benefit.dto.BenefitQuery;
import com.dongqh.luckyhub.benefit.entity.UserBenefit;
import com.dongqh.luckyhub.benefit.enums.BenefitErrorCode;
import com.dongqh.luckyhub.benefit.mapper.UserBenefitMapper;
import com.dongqh.luckyhub.benefit.vo.BenefitView;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.common.enums.CommonErrorCode;
import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.lottery.entity.LotteryDrawRecord;
import com.dongqh.luckyhub.lottery.mapper.LotteryDrawRecordMapper;
import com.dongqh.luckyhub.fulfillment.entity.FulfillmentTask;
import com.dongqh.luckyhub.fulfillment.mapper.FulfillmentTaskMapper;
import com.dongqh.luckyhub.shipping.entity.ShippingOrder;
import com.dongqh.luckyhub.shipping.mapper.ShippingOrderMapper;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import com.dongqh.luckyhub.rbac.service.DataScopeService;
import com.dongqh.luckyhub.rbac.service.UserDataScope;
import org.springframework.stereotype.Service;

import java.util.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BenefitQueryServiceImpl implements BenefitQueryService {
    private static final long MAX_PAGE=1_000_000L;
    private static final LocalDate MYSQL_MIN_DATE=LocalDate.of(1000,1,1);
    private static final LocalDate MAX_SAFE_END_DATE=LocalDate.of(9999,12,30);
    private final UserBenefitMapper benefitMapper; private final LotteryDrawRecordMapper recordMapper;
    private final FulfillmentTaskMapper fulfillmentTaskMapper;
    private final ShippingOrderMapper shippingOrderMapper;
    private final DataScopeService dataScopeService;
    public BenefitQueryServiceImpl(UserBenefitMapper benefitMapper, LotteryDrawRecordMapper recordMapper,
                                   FulfillmentTaskMapper fulfillmentTaskMapper, ShippingOrderMapper shippingOrderMapper,
                                   DataScopeService dataScopeService) {
        this.benefitMapper=benefitMapper; this.recordMapper=recordMapper;
        this.fulfillmentTaskMapper=fulfillmentTaskMapper; this.shippingOrderMapper=shippingOrderMapper; this.dataScopeService=dataScopeService;
    }
    @Override public PageResponse<BenefitView> page(BenefitQuery query) {
        validate(query);
        UserDataScope scope=dataScopeService.resolveUserScope(query.getUserId(), PermissionCodes.BENEFIT_READ_ALL);
        LambdaQueryWrapper<UserBenefit> wrapper=new LambdaQueryWrapper<UserBenefit>()
                .eq(!scope.all(),UserBenefit::getUserId,scope.userId())
                .eq(query.getStatus()!=null,UserBenefit::getStatus,query.getStatus())
                .eq(query.getPrizeType()!=null,UserBenefit::getPrizeType,query.getPrizeType())
                .ge(query.getStartDate()!=null,UserBenefit::getObtainedAt,query.getStartDate()==null?null:query.getStartDate().atStartOfDay())
                .lt(query.getEndDate()!=null,UserBenefit::getObtainedAt,endExclusive(query.getEndDate()))
                .orderByDesc(UserBenefit::getObtainedAt).orderByDesc(UserBenefit::getId);
        Page<UserBenefit> page=benefitMapper.selectPage(Page.of(query.getPage(),query.getSize()),wrapper);
        Map<Long,LotteryDrawRecord> records=records(page.getRecords());
        Map<String,FulfillmentTask> tasks=tasks(page.getRecords());
        List<BenefitView> views=page.getRecords().stream().map(b->toView(b,records.get(b.getDrawRecordId()),
                b.getFulfillmentNo()==null?null:tasks.get(b.getFulfillmentNo()))).toList();
        return new PageResponse<>(views,page.getTotal(),page.getCurrent(),page.getSize(),page.getPages());
    }
    private void validate(BenefitQuery query){
        if(query.getPage()<1||query.getPage()>MAX_PAGE||query.getSize()<1||query.getSize()>100)throw invalidQuery();
        try{Math.multiplyExact(query.getPage()-1,query.getSize());}catch(ArithmeticException exception){throw invalidQuery();}
        validateDate(query.getStartDate(),false);validateDate(query.getEndDate(),true);
        if(query.getStartDate()!=null&&query.getEndDate()!=null&&query.getStartDate().isAfter(query.getEndDate()))throw invalidQuery();
    }
    private void validateDate(LocalDate date,boolean safeEnd){if(date==null)return;LocalDate maximum=safeEnd?MAX_SAFE_END_DATE:MAX_SAFE_END_DATE.plusDays(1);if(date.isBefore(MYSQL_MIN_DATE)||date.isAfter(maximum))throw invalidQuery();}
    private LocalDateTime endExclusive(LocalDate date){return date==null?null:date.plusDays(1).atStartOfDay();}
    private BusinessException invalidQuery(){return new BusinessException(CommonErrorCode.INVALID_PARAMETER,"查询条件超出支持范围");}
    @Override public BenefitView getById(long id) {
        UserBenefit benefit=benefitMapper.selectById(id);
        if(benefit==null) throw new BusinessException(BenefitErrorCode.BENEFIT_NOT_FOUND);
        dataScopeService.resolveUserScope(benefit.getUserId(),PermissionCodes.BENEFIT_READ_ALL);
        FulfillmentTask task=benefit.getFulfillmentNo()==null?null:fulfillmentTaskMapper.selectOne(
                new LambdaQueryWrapper<FulfillmentTask>().eq(FulfillmentTask::getFulfillmentNo,benefit.getFulfillmentNo()));
        return toView(benefit,recordMapper.selectById(benefit.getDrawRecordId()),task);
    }
    private Map<Long,LotteryDrawRecord> records(List<UserBenefit> benefits){
        if(benefits.isEmpty()) return Map.of();
        return recordMapper.selectBatchIds(benefits.stream().map(UserBenefit::getDrawRecordId).toList()).stream()
                .collect(Collectors.toMap(LotteryDrawRecord::getId,Function.identity()));
    }
    private Map<String,FulfillmentTask> tasks(List<UserBenefit> benefits){
        List<String> numbers=benefits.stream().map(UserBenefit::getFulfillmentNo)
                .filter(Objects::nonNull).distinct().toList();
        if(numbers.isEmpty())return Map.of();
        return fulfillmentTaskMapper.selectList(new LambdaQueryWrapper<FulfillmentTask>()
                        .in(FulfillmentTask::getFulfillmentNo,numbers)).stream()
                .collect(Collectors.toMap(FulfillmentTask::getFulfillmentNo,Function.identity()));
    }
    private BenefitView toView(UserBenefit benefit,LotteryDrawRecord record,FulfillmentTask task){
        ShippingOrder shipping=benefit.getShippingOrderId()==null?null:shippingOrderMapper.selectById(benefit.getShippingOrderId());
        return new BenefitView(benefit.getId(),benefit.getDrawRecordId(),benefit.getUserId(),benefit.getPrizeId(),
                benefit.getPrizeType(),record==null?null:record.getPrizeName(),record==null?null:record.getPrizeImageUrl(),
                benefit.getQuantity(),benefit.getStatus(),benefit.getObtainedAt(),benefit.getExpireAt(),
                benefit.getRewardDefinitionId(),benefit.getRewardType(),benefit.getRewardQuantity(),
                benefit.getFulfillmentNo(),task==null?null:task.getStatus(),
                shipping==null?null:shipping.getShippingNo(),shipping==null?null:shipping.getStatus());
    }
}
