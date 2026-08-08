package com.dongqh.luckyhub.points;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.points.dto.AdminPointsAdjustmentCommand;
import com.dongqh.luckyhub.points.dto.PointsMutationCommand;
import com.dongqh.luckyhub.points.dto.PointsReversalCommand;
import com.dongqh.luckyhub.points.entity.PointsAccount;
import com.dongqh.luckyhub.points.entity.PointsLedger;
import com.dongqh.luckyhub.points.enums.PointsBusinessType;
import com.dongqh.luckyhub.points.enums.PointsDirection;
import com.dongqh.luckyhub.points.enums.PointsErrorCode;
import com.dongqh.luckyhub.points.mapper.PointsAccountMapper;
import com.dongqh.luckyhub.points.mapper.PointsLedgerMapper;
import com.dongqh.luckyhub.points.vo.PointsAccountView;
import com.dongqh.luckyhub.points.vo.PointsLedgerView;
import com.dongqh.luckyhub.rbac.entity.SysUser;
import com.dongqh.luckyhub.rbac.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

@SpringBootTest
class PointsAccountServiceTests {

    private static final String SERVICE_CLASS =
            "com.dongqh.luckyhub.points.service.PointsAccountService";

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private PointsAccountMapper accountMapper;

    @Autowired
    private PointsLedgerMapper ledgerMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> userIds = new ArrayList<>();

    @BeforeEach
    void cleanPointsTables() {
        jdbcTemplate.update("DELETE FROM points_ledger");
        jdbcTemplate.update("DELETE FROM points_account");
    }

    @AfterEach
    void cleanUsers() {
        jdbcTemplate.update("DELETE FROM points_ledger");
        jdbcTemplate.update("DELETE FROM points_account");
        userIds.forEach(userMapper::deleteById);
        userIds.clear();
    }

    @Test
    void returnsZeroForEnabledUserWithoutAccount() {
        long userId = createUser(1);

        PointsAccountView view = get(userId);

        assertThat(view.userId()).isEqualTo(userId);
        assertThat(view.balance()).isZero();
        assertThat(view.updatedAt()).isNull();
        assertThat(accountCount(userId)).isZero();
    }

    @Test
    void adjustsCreditsDebitsAndPersistsBalanceSnapshots() {
        long userId = createUser(1);

        PointsLedgerView credited = adjust(new AdminPointsAdjustmentCommand(
                userId, 1_000L, " ADJUST-CREDIT ", "活动补发"));
        PointsLedgerView debited = adjust(new AdminPointsAdjustmentCommand(
                userId, -300L, "ADJUST-DEBIT", "撤销多发"));

        assertThat(credited.businessType()).isEqualTo(PointsBusinessType.MANUAL_ADJUSTMENT);
        assertThat(credited.businessId()).isEqualTo("ADJUST-CREDIT");
        assertThat(credited.direction()).isEqualTo(PointsDirection.CREDIT);
        assertThat(credited.amount()).isEqualTo(1_000L);
        assertThat(credited.balanceAfter()).isEqualTo(1_000L);
        assertThat(debited.direction()).isEqualTo(PointsDirection.DEBIT);
        assertThat(debited.amount()).isEqualTo(300L);
        assertThat(debited.balanceAfter()).isEqualTo(700L);
        assertThat(get(userId).balance()).isEqualTo(700L);
        assertThat(ledgerCount()).isEqualTo(2);
    }

    @Test
    void debitsAvailableBalanceAndRollsBackInsufficientAttempt() {
        long userId = createUser(1);
        adjust(new AdminPointsAdjustmentCommand(userId, 700L, "SEED-700", "测试入账"));

        PointsLedgerView debit = debit(new PointsMutationCommand(
                userId, PointsBusinessType.REDEMPTION, "REDEEM-500", 500L, "兑换商品"));

        assertThat(debit.direction()).isEqualTo(PointsDirection.DEBIT);
        assertThat(debit.balanceAfter()).isEqualTo(200L);
        assertError(() -> debit(new PointsMutationCommand(
                        userId, PointsBusinessType.REDEMPTION, "REDEEM-201", 201L, "余额不足")),
                PointsErrorCode.POINTS_INSUFFICIENT);
        assertThat(get(userId).balance()).isEqualTo(200L);
        assertThat(findLedger(PointsBusinessType.REDEMPTION, "REDEEM-201")).isNull();
    }

    @Test
    void repeatsSameBusinessIdentityWithoutSecondMutation() {
        long userId = createUser(1);
        PointsMutationCommand command = new PointsMutationCommand(
                userId, PointsBusinessType.LOTTERY_REWARD, " LOTTERY-POINTS-1 ", 100L, "中奖积分");

        PointsLedgerView first = credit(command);
        PointsLedgerView repeated = credit(new PointsMutationCommand(
                userId, PointsBusinessType.LOTTERY_REWARD, "LOTTERY-POINTS-1", 100L, "重复请求"));

        assertThat(repeated).isEqualTo(first);
        assertThat(get(userId).balance()).isEqualTo(100L);
        assertThat(ledgerCount()).isEqualTo(1);
    }

    @Test
    void rejectsIdempotencyReuseWithDifferentUserAmountOrDirection() {
        long firstUser = createUser(1);
        long secondUser = createUser(1);
        credit(new PointsMutationCommand(firstUser, PointsBusinessType.LOTTERY_REWARD,
                "IDEMPOTENCY-1", 100L, null));

        assertError(() -> credit(new PointsMutationCommand(secondUser, PointsBusinessType.LOTTERY_REWARD,
                        "IDEMPOTENCY-1", 100L, null)),
                PointsErrorCode.POINTS_IDEMPOTENCY_CONFLICT);
        assertError(() -> credit(new PointsMutationCommand(firstUser, PointsBusinessType.LOTTERY_REWARD,
                        "IDEMPOTENCY-1", 101L, null)),
                PointsErrorCode.POINTS_IDEMPOTENCY_CONFLICT);

        adjust(new AdminPointsAdjustmentCommand(firstUser, 50L, "ADJUST-DIRECTION", "先增加"));
        assertError(() -> adjust(new AdminPointsAdjustmentCommand(
                        firstUser, -50L, "ADJUST-DIRECTION", "再扣减")),
                PointsErrorCode.POINTS_IDEMPOTENCY_CONFLICT);
        assertThat(get(firstUser).balance()).isEqualTo(150L);
        assertThat(accountCount(secondUser)).isZero();
    }

    @Test
    void reversesRedemptionDebitWithAdditiveImmutableLedger() {
        long userId = createUser(1);
        adjust(new AdminPointsAdjustmentCommand(userId, 1_000L, "SEED-REVERSAL", "测试入账"));
        PointsLedgerView debit = debit(new PointsMutationCommand(
                userId, PointsBusinessType.REDEMPTION, "REDEEM-300", 300L, "兑换"));
        PointsLedger originalBefore = ledgerMapper.selectById(debit.id());

        PointsLedgerView reversal = reverse(new PointsReversalCommand(
                userId, PointsBusinessType.REDEMPTION, "REDEEM-300",
                "REVERSAL-300", "履约失败返还"));
        PointsLedger originalAfter = ledgerMapper.selectById(debit.id());

        assertThat(reversal.businessType()).isEqualTo(PointsBusinessType.REVERSAL);
        assertThat(reversal.direction()).isEqualTo(PointsDirection.CREDIT);
        assertThat(reversal.amount()).isEqualTo(300L);
        assertThat(reversal.balanceAfter()).isEqualTo(1_000L);
        assertThat(reversal.reversalOfLedgerId()).isEqualTo(debit.id());
        assertThat(originalAfter.getDirection()).isEqualTo(originalBefore.getDirection());
        assertThat(originalAfter.getAmount()).isEqualTo(originalBefore.getAmount());
        assertThat(originalAfter.getBalanceAfter()).isEqualTo(originalBefore.getBalanceAfter());
        assertThat(originalAfter.getReversalOfLedgerId()).isNull();
    }

    @Test
    void repeatsSameReversalAndRejectsDifferentReversalNumber() {
        long userId = createUser(1);
        adjust(new AdminPointsAdjustmentCommand(userId, 500L, "SEED-REVERSE-TWICE", "测试入账"));
        debit(new PointsMutationCommand(userId, PointsBusinessType.REDEMPTION,
                "REDEEM-REVERSE-TWICE", 200L, null));
        PointsReversalCommand command = new PointsReversalCommand(
                userId, PointsBusinessType.REDEMPTION, "REDEEM-REVERSE-TWICE",
                "REVERSAL-SAME", "第一次冲正");

        PointsLedgerView first = reverse(command);
        PointsLedgerView repeated = reverse(new PointsReversalCommand(
                userId, PointsBusinessType.REDEMPTION, "REDEEM-REVERSE-TWICE",
                " REVERSAL-SAME ", "重复冲正"));

        assertThat(repeated).isEqualTo(first);
        assertThat(get(userId).balance()).isEqualTo(500L);
        assertError(() -> reverse(new PointsReversalCommand(
                        userId, PointsBusinessType.REDEMPTION, "REDEEM-REVERSE-TWICE",
                        "REVERSAL-DIFFERENT", "错误的第二次冲正")),
                PointsErrorCode.POINTS_REVERSAL_CONFLICT);
        assertThat(ledgerCountByType(PointsBusinessType.REVERSAL)).isEqualTo(1);
    }

    @Test
    void rejectsMissingOrNonRedemptionOriginalLedger() {
        long userId = createUser(1);
        assertError(() -> reverse(new PointsReversalCommand(
                        userId, PointsBusinessType.REDEMPTION, "MISSING-DEBIT",
                        "REVERSAL-MISSING", null)),
                PointsErrorCode.POINTS_LEDGER_NOT_FOUND);

        credit(new PointsMutationCommand(userId, PointsBusinessType.LOTTERY_REWARD,
                "NOT-A-DEBIT", 100L, null));
        assertError(() -> reverse(new PointsReversalCommand(
                        userId, PointsBusinessType.LOTTERY_REWARD, "NOT-A-DEBIT",
                        "REVERSAL-NON-DEBIT", null)),
                PointsErrorCode.POINTS_REVERSAL_CONFLICT);
        assertThat(ledgerCountByType(PointsBusinessType.REVERSAL)).isZero();
    }

    @Test
    void rejectsMissingAndDisabledUsersWithoutCreatingAssets() {
        long disabledUser = createUser(0);

        assertError(() -> credit(new PointsMutationCommand(
                        Long.MAX_VALUE, PointsBusinessType.LOTTERY_REWARD,
                        "MISSING-USER", 10L, null)),
                PointsErrorCode.POINTS_USER_UNAVAILABLE);
        assertError(() -> adjust(new AdminPointsAdjustmentCommand(
                        disabledUser, 10L, "DISABLED-USER", "不应入账")),
                PointsErrorCode.POINTS_USER_UNAVAILABLE);

        assertThat(accountCount(Long.MAX_VALUE)).isZero();
        assertThat(accountCount(disabledUser)).isZero();
        assertThat(ledgerCount()).isZero();
    }

    @Test
    void rejectsInvalidAmountsBusinessTypesAndAdditionOverflow() {
        long userId = createUser(1);

        assertError(() -> credit(new PointsMutationCommand(
                        userId, PointsBusinessType.LOTTERY_REWARD, "ZERO", 0L, null)),
                PointsErrorCode.POINTS_AMOUNT_INVALID);
        assertError(() -> credit(new PointsMutationCommand(
                        userId, PointsBusinessType.LOTTERY_REWARD, "MIN", Long.MIN_VALUE, null)),
                PointsErrorCode.POINTS_AMOUNT_INVALID);
        assertError(() -> credit(new PointsMutationCommand(
                        userId, PointsBusinessType.REDEMPTION, "WRONG-CREDIT-TYPE", 10L, null)),
                PointsErrorCode.POINTS_AMOUNT_INVALID);
        assertError(() -> debit(new PointsMutationCommand(
                        userId, PointsBusinessType.ORDER_REWARD, "WRONG-DEBIT-TYPE", 10L, null)),
                PointsErrorCode.POINTS_AMOUNT_INVALID);

        credit(new PointsMutationCommand(userId, PointsBusinessType.LOTTERY_REWARD,
                "MAX-CREDIT", Long.MAX_VALUE, null));
        assertError(() -> credit(new PointsMutationCommand(
                        userId, PointsBusinessType.ORDER_REWARD, "OVERFLOW", 1L, null)),
                PointsErrorCode.POINTS_AMOUNT_INVALID);
        assertThat(get(userId).balance()).isEqualTo(Long.MAX_VALUE);
        assertThat(findLedger(PointsBusinessType.ORDER_REWARD, "OVERFLOW")).isNull();
    }

    private long createUser(int status) {
        SysUser user = new SysUser();
        user.setUsername("points-" + UUID.randomUUID());
        user.setPassword("test-password");
        user.setNickname("积分测试用户");
        user.setStatus(status);
        userMapper.insert(user);
        userIds.add(user.getId());
        return user.getId();
    }

    private PointsLedgerView credit(PointsMutationCommand command) {
        return invoke("credit", PointsMutationCommand.class, command, PointsLedgerView.class);
    }

    private PointsLedgerView debit(PointsMutationCommand command) {
        return invoke("debit", PointsMutationCommand.class, command, PointsLedgerView.class);
    }

    private PointsLedgerView reverse(PointsReversalCommand command) {
        return invoke("reverseDebit", PointsReversalCommand.class, command, PointsLedgerView.class);
    }

    private PointsLedgerView adjust(AdminPointsAdjustmentCommand command) {
        return invoke("adjust", AdminPointsAdjustmentCommand.class, command, PointsLedgerView.class);
    }

    private PointsAccountView get(long userId) {
        return invoke("get", long.class, userId, PointsAccountView.class);
    }

    private <T> T invoke(String methodName, Class<?> parameterType, Object argument, Class<T> resultType) {
        Object service = requiredService();
        try {
            Object result = service.getClass().getMethod(methodName, parameterType).invoke(service, argument);
            return resultType.cast(result);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (exception.getCause() instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(exception.getCause());
        } catch (ReflectiveOperationException exception) {
            return fail("积分账户服务缺少方法: " + methodName, exception);
        }
    }

    private Object requiredService() {
        try {
            return applicationContext.getBean(Class.forName(SERVICE_CLASS));
        } catch (ClassNotFoundException exception) {
            return fail("缺少积分账户服务接口: " + SERVICE_CLASS, exception);
        }
    }

    private PointsLedger findLedger(PointsBusinessType businessType, String businessId) {
        return ledgerMapper.selectOne(new LambdaQueryWrapper<PointsLedger>()
                .eq(PointsLedger::getBusinessType, businessType)
                .eq(PointsLedger::getBusinessId, businessId));
    }

    private int accountCount(long userId) {
        Long count = accountMapper.selectCount(new LambdaQueryWrapper<PointsAccount>()
                .eq(PointsAccount::getUserId, userId));
        return count == null ? 0 : count.intValue();
    }

    private int ledgerCount() {
        Long count = ledgerMapper.selectCount(null);
        return count == null ? 0 : count.intValue();
    }

    private int ledgerCountByType(PointsBusinessType businessType) {
        Long count = ledgerMapper.selectCount(new LambdaQueryWrapper<PointsLedger>()
                .eq(PointsLedger::getBusinessType, businessType));
        return count == null ? 0 : count.intValue();
    }

    private void assertError(Runnable action, PointsErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
