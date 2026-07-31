package com.dongqh.luckyhub.benefit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dongqh.luckyhub.benefit.entity.UserBenefit;
import com.dongqh.luckyhub.benefit.enums.BenefitErrorCode;
import com.dongqh.luckyhub.benefit.enums.BenefitStatus;
import com.dongqh.luckyhub.benefit.handler.BenefitFulfillmentHandler;
import com.dongqh.luckyhub.benefit.handler.BenefitFulfillmentRouter;
import com.dongqh.luckyhub.benefit.mapper.UserBenefitMapper;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.lottery.config.MessagingProperties;
import com.dongqh.luckyhub.lottery.entity.MessageConsumeRecord;
import com.dongqh.luckyhub.lottery.mapper.MessageConsumeRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

@Service
public class BenefitFulfillmentServiceImpl implements BenefitFulfillmentService {

    private static final String SAFE_GRANT_ERROR = BenefitErrorCode.BENEFIT_GRANT_FAILED.message();

    private final UserBenefitMapper benefitMapper;
    private final MessageConsumeRecordMapper consumeRecordMapper;
    private final BenefitFulfillmentRouter router;
    private final String consumerName;
    private final TransactionTemplate fulfillmentTransaction;
    private final TransactionTemplate failureTransaction;

    public BenefitFulfillmentServiceImpl(
            UserBenefitMapper benefitMapper,
            MessageConsumeRecordMapper consumeRecordMapper,
            BenefitFulfillmentRouter router,
            MessagingProperties properties,
            PlatformTransactionManager transactionManager) {
        this.benefitMapper = benefitMapper;
        this.consumeRecordMapper = consumeRecordMapper;
        this.router = router;
        this.consumerName = properties.logicalConsumerName();
        this.fulfillmentTransaction = new TransactionTemplate(transactionManager);
        this.failureTransaction = new TransactionTemplate(transactionManager);
        this.failureTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void fulfill(long benefitId, String eventId) {
        if (benefitId <= 0 || eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("benefitId must be positive and eventId must not be blank");
        }
        if (alreadyConsumed(eventId)) {
            return;
        }
        try {
            fulfillmentTransaction.executeWithoutResult(status -> fulfillAndRecord(benefitId, eventId));
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == BenefitErrorCode.BENEFIT_NOT_FOUND
                    || exception.getErrorCode() == BenefitErrorCode.BENEFIT_STATE_CONFLICT) {
                throw exception;
            }
            persistSafeFailure(benefitId);
            throw new BusinessException(BenefitErrorCode.BENEFIT_GRANT_FAILED);
        } catch (RuntimeException exception) {
            persistSafeFailure(benefitId);
            throw new BusinessException(BenefitErrorCode.BENEFIT_GRANT_FAILED);
        }
    }

    private void fulfillAndRecord(long benefitId, String eventId) {
        UserBenefit benefit = benefitMapper.selectByIdForUpdate(benefitId);
        if (alreadyConsumed(eventId)) {
            return;
        }
        if (benefit == null) {
            throw new BusinessException(BenefitErrorCode.BENEFIT_NOT_FOUND);
        }
        BenefitStatus current = benefit.getStatus();
        if (current != BenefitStatus.PENDING && current != BenefitStatus.GRANT_FAILED) {
            throw new BusinessException(BenefitErrorCode.BENEFIT_STATE_CONFLICT);
        }

        BenefitFulfillmentHandler handler = router.route(benefit.getPrizeType());
        BenefitStatus target = handler.fulfill(benefit, eventId);
        validateTarget(benefit, target);
        if (benefitMapper.transitionStatus(benefitId, current, target) != 1) {
            throw new BusinessException(BenefitErrorCode.BENEFIT_STATE_CONFLICT);
        }

        MessageConsumeRecord record = new MessageConsumeRecord();
        record.setEventId(eventId);
        record.setConsumerName(consumerName);
        record.setConsumedAt(LocalDateTime.now());
        consumeRecordMapper.insert(record);
    }

    private void validateTarget(UserBenefit benefit, BenefitStatus target) {
        BenefitStatus expected = benefit.getPrizeType() == com.dongqh.luckyhub.prize.enums.PrizeType.PHYSICAL
                ? BenefitStatus.CLAIM_PENDING : BenefitStatus.AVAILABLE;
        if (target != expected) {
            throw new IllegalStateException("Fulfillment handler returned an invalid target state");
        }
    }

    private boolean alreadyConsumed(String eventId) {
        return consumeRecordMapper.selectCount(new LambdaQueryWrapper<MessageConsumeRecord>()
                .eq(MessageConsumeRecord::getEventId, eventId)
                .eq(MessageConsumeRecord::getConsumerName, consumerName)) > 0;
    }

    private void persistSafeFailure(long benefitId) {
        failureTransaction.executeWithoutResult(status ->
                benefitMapper.markGrantFailed(benefitId, SAFE_GRANT_ERROR));
    }
}
