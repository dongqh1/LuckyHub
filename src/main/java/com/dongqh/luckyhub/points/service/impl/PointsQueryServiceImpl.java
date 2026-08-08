package com.dongqh.luckyhub.points.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.points.dto.PointsLedgerQuery;
import com.dongqh.luckyhub.points.entity.PointsLedger;
import com.dongqh.luckyhub.points.mapper.PointsLedgerMapper;
import com.dongqh.luckyhub.points.service.PointsAccountService;
import com.dongqh.luckyhub.points.service.PointsQueryService;
import com.dongqh.luckyhub.points.vo.PointsAccountView;
import com.dongqh.luckyhub.points.vo.PointsLedgerView;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class PointsQueryServiceImpl implements PointsQueryService {

    private final PointsAccountService accountService;
    private final PointsLedgerMapper ledgerMapper;

    public PointsQueryServiceImpl(
            PointsAccountService accountService,
            PointsLedgerMapper ledgerMapper
    ) {
        this.accountService = accountService;
        this.ledgerMapper = ledgerMapper;
    }

    @Override
    public PointsAccountView getAccount(long userId) {
        return accountService.get(userId);
    }

    @Override
    public PageResponse<PointsLedgerView> pageLedgers(long userId, PointsLedgerQuery query) {
        String businessId = StringUtils.hasText(query.getBusinessId())
                ? query.getBusinessId().trim() : null;
        LambdaQueryWrapper<PointsLedger> wrapper = new LambdaQueryWrapper<PointsLedger>()
                .eq(PointsLedger::getUserId, userId)
                .eq(businessId != null, PointsLedger::getBusinessId, businessId)
                .eq(query.getBusinessType() != null,
                        PointsLedger::getBusinessType, query.getBusinessType())
                .eq(query.getDirection() != null,
                        PointsLedger::getDirection, query.getDirection())
                .orderByDesc(PointsLedger::getCreatedAt)
                .orderByDesc(PointsLedger::getId);
        Page<PointsLedger> result = ledgerMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);
        List<PointsLedgerView> records = result.getRecords().stream().map(this::view).toList();
        return new PageResponse<>(records, result.getTotal(), result.getCurrent(),
                result.getSize(), result.getPages());
    }

    private PointsLedgerView view(PointsLedger ledger) {
        return new PointsLedgerView(
                ledger.getId(), ledger.getUserId(), ledger.getBusinessType(),
                ledger.getBusinessId(), ledger.getDirection(), ledger.getAmount(),
                ledger.getBalanceAfter(), ledger.getReversalOfLedgerId(),
                ledger.getRemark(), ledger.getCreatedAt());
    }
}
