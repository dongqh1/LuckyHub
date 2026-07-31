package com.dongqh.luckyhub.lottery.service.impl;

import com.dongqh.luckyhub.benefit.entity.UserBenefit;
import com.dongqh.luckyhub.benefit.enums.BenefitStatus;
import com.dongqh.luckyhub.benefit.mapper.UserBenefitMapper;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.inventory.service.ActivityPrizeInventoryService;
import com.dongqh.luckyhub.lottery.algorithm.DrawCandidate;
import com.dongqh.luckyhub.lottery.algorithm.PrizeWeightSnapshot;
import com.dongqh.luckyhub.lottery.algorithm.WeightedDrawEngine;
import com.dongqh.luckyhub.lottery.entity.LotteryDrawRecord;
import com.dongqh.luckyhub.lottery.enums.DrawOrderStatus;
import com.dongqh.luckyhub.lottery.enums.DrawResultType;
import com.dongqh.luckyhub.lottery.enums.LotteryErrorCode;
import com.dongqh.luckyhub.lottery.mapper.LotteryDrawOrderMapper;
import com.dongqh.luckyhub.lottery.mapper.LotteryDrawRecordMapper;
import com.dongqh.luckyhub.lottery.messaging.event.DrawConfirmedEvent;
import com.dongqh.luckyhub.lottery.messaging.event.DrawEventEnvelope;
import com.dongqh.luckyhub.lottery.messaging.event.DrawEventType;
import com.dongqh.luckyhub.lottery.messaging.event.PrizeFulfillmentRequestedEvent;
import com.dongqh.luckyhub.lottery.model.DrawExecutionContext;
import com.dongqh.luckyhub.lottery.model.DrawExecutionResult;
import com.dongqh.luckyhub.lottery.model.DrawPrizeSnapshot;
import com.dongqh.luckyhub.lottery.model.DrawResultItem;
import com.dongqh.luckyhub.lottery.service.DrawTransactionService;
import com.dongqh.luckyhub.lottery.service.OutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@Service
public class DrawTransactionServiceImpl implements DrawTransactionService {

    private final WeightedDrawEngine drawEngine;
    private final ActivityPrizeInventoryService inventoryService;
    private final LotteryDrawOrderMapper orderMapper;
    private final LotteryDrawRecordMapper recordMapper;
    private final UserBenefitMapper benefitMapper;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    public DrawTransactionServiceImpl(
            WeightedDrawEngine drawEngine,
            ActivityPrizeInventoryService inventoryService,
            LotteryDrawOrderMapper orderMapper,
            LotteryDrawRecordMapper recordMapper,
            UserBenefitMapper benefitMapper,
            OutboxService outboxService,
            ObjectMapper objectMapper) {
        this.drawEngine = drawEngine;
        this.inventoryService = inventoryService;
        this.orderMapper = orderMapper;
        this.recordMapper = recordMapper;
        this.benefitMapper = benefitMapper;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public DrawExecutionResult execute(DrawExecutionContext context) {
        List<PrizeWeightSnapshot> weights = context.prizes().stream()
                .map(DrawPrizeSnapshot::toWeightSnapshot)
                .toList();
        List<DrawResultItem> results = new ArrayList<>(context.drawCount());

        for (int sequence = 1; sequence <= context.drawCount(); sequence++) {
            DrawCandidate candidate = drawEngine.select(weights, context.noWinWeight());
            DrawPrizeSnapshot wonPrize = resolveFinalWin(candidate, context.prizes());
            results.add(persistResult(context, sequence, wonPrize));
        }

        if (orderMapper.markSuccessIfProcessing(context.orderId(), context.drawTime()) != 1) {
            throw new BusinessException(LotteryErrorCode.DRAW_TRANSACTION_FAILED);
        }

        appendConfirmedEvent(context);
        results.stream()
                .filter(item -> item.resultType() == DrawResultType.WIN)
                .forEach(item -> appendFulfillmentEvent(context, item));

        return new DrawExecutionResult(
                context.orderId(), context.requestId(), DrawOrderStatus.SUCCESS,
                context.drawTime(), results);
    }

    private DrawPrizeSnapshot resolveFinalWin(
            DrawCandidate candidate,
            List<DrawPrizeSnapshot> snapshots) {
        if (candidate.type() == DrawCandidate.Type.NO_WIN) {
            return null;
        }
        DrawPrizeSnapshot snapshot = snapshots.stream()
                .filter(prize -> prize.activityPrizeId() == candidate.activityPrizeId()
                        && prize.prizeId() == candidate.prizeId())
                .findFirst()
                .orElseThrow(() -> new BusinessException(LotteryErrorCode.DRAW_TRANSACTION_FAILED));
        return inventoryService.decrementIfAvailable(snapshot.activityPrizeId()) ? snapshot : null;
    }

    private DrawResultItem persistResult(
            DrawExecutionContext context,
            int sequence,
            DrawPrizeSnapshot prize) {
        LotteryDrawRecord record = new LotteryDrawRecord();
        record.setOrderId(context.orderId());
        record.setRequestId(context.requestId());
        record.setSequenceNo(sequence);
        record.setUserId(context.userId());
        record.setActivityId(context.activityId());
        record.setResultType(prize == null ? DrawResultType.NO_WIN : DrawResultType.WIN);
        record.setDrawTime(context.drawTime());
        if (prize != null) {
            record.setPrizeId(prize.prizeId());
            record.setPrizeName(prize.prizeName());
            record.setPrizeType(prize.prizeType());
            record.setPrizeImageUrl(prize.prizeImageUrl());
        }
        recordMapper.insert(record);

        Long benefitId = prize == null ? null : persistPendingBenefit(context, record, prize);
        return new DrawResultItem(
                record.getId(), sequence, record.getResultType(), record.getPrizeId(),
                record.getPrizeName(), record.getPrizeType(), record.getPrizeImageUrl(), benefitId);
    }

    private Long persistPendingBenefit(
            DrawExecutionContext context,
            LotteryDrawRecord record,
            DrawPrizeSnapshot prize) {
        UserBenefit benefit = new UserBenefit();
        benefit.setDrawRecordId(record.getId());
        benefit.setUserId(context.userId());
        benefit.setPrizeId(prize.prizeId());
        benefit.setPrizeType(prize.prizeType());
        benefit.setQuantity(1);
        benefit.setStatus(BenefitStatus.PENDING);
        benefit.setObtainedAt(context.drawTime());
        benefitMapper.insert(benefit);
        return benefit.getId();
    }

    private void appendConfirmedEvent(DrawExecutionContext context) {
        outboxService.append(DrawEventEnvelope.create(
                DrawEventType.DRAW_CONFIRMED,
                context.requestId(), context.userId(), context.activityId(), context.orderId(),
                context.drawTime(), new DrawConfirmedEvent(context.drawCount(), context.drawDate()),
                objectMapper));
    }

    private void appendFulfillmentEvent(DrawExecutionContext context, DrawResultItem item) {
        outboxService.append(DrawEventEnvelope.create(
                DrawEventType.PRIZE_FULFILLMENT_REQUESTED,
                context.requestId(), context.userId(), context.activityId(), context.orderId(),
                context.drawTime(), new PrizeFulfillmentRequestedEvent(
                        item.benefitId(), item.recordId(), item.prizeId(), item.prizeType()),
                objectMapper));
    }
}
