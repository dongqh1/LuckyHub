package com.dongqh.luckyhub.lottery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.drawchance.enums.DrawChanceErrorCode;
import com.dongqh.luckyhub.drawchance.service.DrawChanceService;
import com.dongqh.luckyhub.lottery.config.MessagingProperties;
import com.dongqh.luckyhub.lottery.entity.MessageConsumeRecord;
import com.dongqh.luckyhub.lottery.mapper.MessageConsumeRecordMapper;
import com.dongqh.luckyhub.lottery.messaging.event.DrawConfirmedEvent;
import com.dongqh.luckyhub.lottery.messaging.event.DrawEventEnvelope;
import com.dongqh.luckyhub.lottery.messaging.event.DrawReleaseRequestedEvent;
import com.dongqh.luckyhub.lottery.messaging.event.PrizeFulfillmentRequestedEvent;
import com.dongqh.luckyhub.lottery.quota.DrawQuotaService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

@Service
public class MessageConsumeService {

    private final MessageConsumeRecordMapper consumeRecordMapper;
    private final DrawQuotaService quotaService;
    private final DrawChanceService drawChanceService;
    private final LotteryRewardDispatchService rewardDispatchService;
    private final ObjectMapper objectMapper;
    private final String logicalConsumerName;
    private final TransactionTemplate transactionTemplate;

    public MessageConsumeService(
            MessageConsumeRecordMapper consumeRecordMapper,
            DrawQuotaService quotaService,
            DrawChanceService drawChanceService,
            LotteryRewardDispatchService rewardDispatchService,
            ObjectMapper objectMapper,
            MessagingProperties properties,
            PlatformTransactionManager transactionManager) {
        this.consumeRecordMapper = consumeRecordMapper;
        this.quotaService = quotaService;
        this.drawChanceService = drawChanceService;
        this.rewardDispatchService = rewardDispatchService;
        this.objectMapper = objectMapper;
        this.logicalConsumerName = properties.logicalConsumerName();
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Performs the idempotent Redis quota transition before opening the short MySQL
     * transaction that records consumption. If the database write fails, Stream is
     * not acknowledged; redelivery safely repeats the Lua transition and record insert.
     */
    public void consume(DrawEventEnvelope event) {
        String eventId = event.eventId().toString();
        if (alreadyConsumed(eventId)) {
            return;
        }

        switch (event.eventType()) {
            case DRAW_CONFIRMED -> {
                event.payloadAs(DrawConfirmedEvent.class, objectMapper);
                quotaService.confirm(event.requestId());
                settleRewardedChance(event.requestId(), true);
            }
            case DRAW_RELEASE_REQUESTED -> {
                event.payloadAs(DrawReleaseRequestedEvent.class, objectMapper);
                quotaService.release(event.requestId());
                settleRewardedChance(event.requestId(), false);
            }
            case PRIZE_FULFILLMENT_REQUESTED -> {
                var payload = event.payloadAs(
                        PrizeFulfillmentRequestedEvent.class, objectMapper);
                rewardDispatchService.dispatch(event, payload);
                return;
            }
        }

        try {
            transactionTemplate.executeWithoutResult(status -> {
                if (!alreadyConsumed(eventId)) {
                    MessageConsumeRecord record = new MessageConsumeRecord();
                    record.setEventId(eventId);
                    record.setConsumerName(logicalConsumerName);
                    record.setConsumedAt(LocalDateTime.now());
                    consumeRecordMapper.insert(record);
                }
            });
        } catch (DataIntegrityViolationException duplicateRace) {
            if (!alreadyConsumed(eventId)) {
                throw duplicateRace;
            }
        }
    }

    private void settleRewardedChance(String requestId, boolean confirmed) {
        try {
            if (confirmed) drawChanceService.confirm(requestId);
            else drawChanceService.release(requestId);
        } catch (BusinessException error) {
            // Events created before rewarded chances were introduced have no MySQL reservation.
            if (error.getErrorCode() != DrawChanceErrorCode.RESERVATION_NOT_FOUND) throw error;
        }
    }

    public boolean alreadyConsumed(String eventId) {
        return consumeRecordMapper.selectCount(new LambdaQueryWrapper<MessageConsumeRecord>()
                .eq(MessageConsumeRecord::getEventId, eventId)
                .eq(MessageConsumeRecord::getConsumerName, logicalConsumerName)) > 0;
    }
}
