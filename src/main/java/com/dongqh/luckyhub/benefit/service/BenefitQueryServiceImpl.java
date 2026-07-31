package com.dongqh.luckyhub.benefit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dongqh.luckyhub.benefit.dto.BenefitQuery;
import com.dongqh.luckyhub.benefit.entity.UserBenefit;
import com.dongqh.luckyhub.benefit.enums.BenefitErrorCode;
import com.dongqh.luckyhub.benefit.mapper.UserBenefitMapper;
import com.dongqh.luckyhub.benefit.vo.BenefitView;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.lottery.entity.LotteryDrawRecord;
import com.dongqh.luckyhub.lottery.mapper.LotteryDrawRecordMapper;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import com.dongqh.luckyhub.rbac.service.DataScopeService;
import com.dongqh.luckyhub.rbac.service.UserDataScope;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BenefitQueryServiceImpl implements BenefitQueryService {
    private final UserBenefitMapper benefitMapper; private final LotteryDrawRecordMapper recordMapper;
    private final DataScopeService dataScopeService;
    public BenefitQueryServiceImpl(UserBenefitMapper benefitMapper, LotteryDrawRecordMapper recordMapper,
                                   DataScopeService dataScopeService) {
        this.benefitMapper=benefitMapper; this.recordMapper=recordMapper; this.dataScopeService=dataScopeService;
    }
    @Override public PageResponse<BenefitView> page(BenefitQuery query) {
        UserDataScope scope=dataScopeService.resolveUserScope(query.getUserId(), PermissionCodes.BENEFIT_READ_ALL);
        LambdaQueryWrapper<UserBenefit> wrapper=new LambdaQueryWrapper<UserBenefit>()
                .eq(!scope.all(),UserBenefit::getUserId,scope.userId())
                .eq(query.getStatus()!=null,UserBenefit::getStatus,query.getStatus())
                .eq(query.getPrizeType()!=null,UserBenefit::getPrizeType,query.getPrizeType())
                .ge(query.getStartDate()!=null,UserBenefit::getObtainedAt,query.getStartDate()==null?null:query.getStartDate().atStartOfDay())
                .lt(query.getEndDate()!=null,UserBenefit::getObtainedAt,query.getEndDate()==null?null:query.getEndDate().plusDays(1).atStartOfDay())
                .orderByDesc(UserBenefit::getObtainedAt).orderByDesc(UserBenefit::getId);
        Page<UserBenefit> page=benefitMapper.selectPage(Page.of(query.getPage(),query.getSize()),wrapper);
        Map<Long,LotteryDrawRecord> records=records(page.getRecords());
        List<BenefitView> views=page.getRecords().stream().map(b->toView(b,records.get(b.getDrawRecordId()))).toList();
        return new PageResponse<>(views,page.getTotal(),page.getCurrent(),page.getSize(),page.getPages());
    }
    @Override public BenefitView getById(long id) {
        UserBenefit benefit=benefitMapper.selectById(id);
        if(benefit==null) throw new BusinessException(BenefitErrorCode.BENEFIT_NOT_FOUND);
        dataScopeService.resolveUserScope(benefit.getUserId(),PermissionCodes.BENEFIT_READ_ALL);
        return toView(benefit,recordMapper.selectById(benefit.getDrawRecordId()));
    }
    private Map<Long,LotteryDrawRecord> records(List<UserBenefit> benefits){
        if(benefits.isEmpty()) return Map.of();
        return recordMapper.selectBatchIds(benefits.stream().map(UserBenefit::getDrawRecordId).toList()).stream()
                .collect(Collectors.toMap(LotteryDrawRecord::getId,Function.identity()));
    }
    private BenefitView toView(UserBenefit benefit,LotteryDrawRecord record){
        return new BenefitView(benefit.getId(),benefit.getDrawRecordId(),benefit.getUserId(),benefit.getPrizeId(),
                benefit.getPrizeType(),record==null?null:record.getPrizeName(),record==null?null:record.getPrizeImageUrl(),
                benefit.getQuantity(),benefit.getStatus(),benefit.getObtainedAt(),benefit.getExpireAt());
    }
}
