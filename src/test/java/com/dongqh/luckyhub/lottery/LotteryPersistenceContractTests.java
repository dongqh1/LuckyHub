package com.dongqh.luckyhub.lottery;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.luckyhub.benefit.entity.UserBenefit;
import com.dongqh.luckyhub.benefit.enums.BenefitStatus;
import com.dongqh.luckyhub.benefit.mapper.UserBenefitMapper;
import com.dongqh.luckyhub.lottery.entity.LotteryDrawOrder;
import com.dongqh.luckyhub.lottery.entity.LotteryDrawRecord;
import com.dongqh.luckyhub.lottery.entity.MessageConsumeRecord;
import com.dongqh.luckyhub.lottery.entity.MessageOutbox;
import com.dongqh.luckyhub.lottery.enums.DrawOrderStatus;
import com.dongqh.luckyhub.lottery.enums.DrawResultType;
import com.dongqh.luckyhub.lottery.enums.OutboxStatus;
import com.dongqh.luckyhub.lottery.mapper.LotteryDrawOrderMapper;
import com.dongqh.luckyhub.lottery.mapper.LotteryDrawRecordMapper;
import com.dongqh.luckyhub.lottery.mapper.MessageConsumeRecordMapper;
import com.dongqh.luckyhub.lottery.mapper.MessageOutboxMapper;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class LotteryPersistenceContractTests {

    @Test
    void definesDatabaseStatesExactly() {
        assertThat(DrawOrderStatus.values()).containsExactly(
                DrawOrderStatus.PROCESSING, DrawOrderStatus.SUCCESS, DrawOrderStatus.FAILED);
        assertThat(DrawResultType.values()).containsExactly(
                DrawResultType.WIN, DrawResultType.NO_WIN);
        assertThat(BenefitStatus.values()).containsExactly(
                BenefitStatus.PENDING, BenefitStatus.AVAILABLE,
                BenefitStatus.CLAIM_PENDING, BenefitStatus.GRANT_FAILED);
        assertThat(OutboxStatus.values()).containsExactly(
                OutboxStatus.PENDING, OutboxStatus.SENT, OutboxStatus.FAILED);
    }

    @Test
    void mapsEntitiesToTheirDatabaseTables() {
        assertTable(LotteryDrawOrder.class, "lottery_draw_order");
        assertTable(LotteryDrawRecord.class, "lottery_draw_record");
        assertTable(UserBenefit.class, "user_benefit");
        assertTable(MessageOutbox.class, "message_outbox");
        assertTable(MessageConsumeRecord.class, "message_consume_record");
    }

    @Test
    void exposesDrawOrderDateAndTimestampsWithStrongTypes() {
        assertField(LotteryDrawOrder.class, "drawDate", LocalDate.class);
        assertField(LotteryDrawOrder.class, "createdAt", LocalDateTime.class);
        assertField(LotteryDrawOrder.class, "completedAt", LocalDateTime.class);
    }

    @Test
    void exposesNullableDrawResultSnapshotFields() {
        assertField(LotteryDrawRecord.class, "resultType", DrawResultType.class);
        assertField(LotteryDrawRecord.class, "prizeId", Long.class);
        assertField(LotteryDrawRecord.class, "prizeName", String.class);
        assertField(LotteryDrawRecord.class, "prizeType", PrizeType.class);
        assertField(LotteryDrawRecord.class, "prizeImageUrl", String.class);
        assertField(LotteryDrawRecord.class, "drawTime", LocalDateTime.class);
    }

    @Test
    void exposesBenefitSourceAndGrantFields() {
        assertField(UserBenefit.class, "drawRecordId", Long.class);
        assertField(UserBenefit.class, "prizeType", PrizeType.class);
        assertField(UserBenefit.class, "status", BenefitStatus.class);
        assertField(UserBenefit.class, "grantError", String.class);
    }

    @Test
    void exposesOutboxPayloadAndRetryFields() {
        assertField(MessageOutbox.class, "payload", String.class);
        assertField(MessageOutbox.class, "status", OutboxStatus.class);
        assertField(MessageOutbox.class, "retryCount", Integer.class);
        assertField(MessageOutbox.class, "lastError", String.class);
        assertField(MessageOutbox.class, "nextRetryAt", LocalDateTime.class);
        assertField(MessageOutbox.class, "createdAt", LocalDateTime.class);
        assertField(MessageOutbox.class, "sentAt", LocalDateTime.class);
        assertField(MessageConsumeRecord.class, "consumedAt", LocalDateTime.class);
    }

    @Test
    void everyMapperTargetsItsExpectedEntity() {
        assertMapperEntity(LotteryDrawOrderMapper.class, LotteryDrawOrder.class);
        assertMapperEntity(LotteryDrawRecordMapper.class, LotteryDrawRecord.class);
        assertMapperEntity(MessageOutboxMapper.class, MessageOutbox.class);
        assertMapperEntity(MessageConsumeRecordMapper.class, MessageConsumeRecord.class);
        assertMapperEntity(UserBenefitMapper.class, UserBenefit.class);
    }

    private static void assertTable(Class<?> entityType, String tableName) {
        assertThat(entityType.getAnnotation(TableName.class)).isNotNull();
        assertThat(entityType.getAnnotation(TableName.class).value()).isEqualTo(tableName);
    }

    private static void assertField(Class<?> entityType, String fieldName, Class<?> fieldType) {
        assertThat(findField(entityType, fieldName).getType()).isEqualTo(fieldType);
    }

    private static Field findField(Class<?> entityType, String fieldName) {
        try {
            return entityType.getDeclaredField(fieldName);
        } catch (NoSuchFieldException exception) {
            throw new AssertionError(entityType.getSimpleName() + " is missing field " + fieldName, exception);
        }
    }

    private static void assertMapperEntity(Class<?> mapperType, Class<?> entityType) {
        assertThat(mapperType.getGenericInterfaces())
                .singleElement()
                .isInstanceOfSatisfying(ParameterizedType.class, genericType -> {
                    assertThat(genericType.getRawType()).isEqualTo(BaseMapper.class);
                    assertThat(genericType.getActualTypeArguments()).containsExactly(entityType);
                });
    }
}
