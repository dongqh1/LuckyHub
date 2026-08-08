package com.dongqh.luckyhub.points.service.impl;

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
import com.dongqh.luckyhub.points.service.PointsAccountService;
import com.dongqh.luckyhub.points.vo.PointsAccountView;
import com.dongqh.luckyhub.points.vo.PointsLedgerView;
import com.dongqh.luckyhub.rbac.entity.SysUser;
import com.dongqh.luckyhub.rbac.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

@Service
public class PointsAccountServiceImpl implements PointsAccountService {

    private static final int ENABLED = 1;
    private static final int MAX_BUSINESS_ID_LENGTH = 100;
    private static final int MAX_REMARK_LENGTH = 500;
    private static final Set<PointsBusinessType> CREDIT_TYPES = EnumSet.of(
            PointsBusinessType.LOTTERY_REWARD,
            PointsBusinessType.ORDER_REWARD,
            PointsBusinessType.MEMBERSHIP_BONUS
    );

    private final SysUserMapper userMapper;
    private final PointsAccountMapper accountMapper;
    private final PointsLedgerMapper ledgerMapper;

    public PointsAccountServiceImpl(
            SysUserMapper userMapper,
            PointsAccountMapper accountMapper,
            PointsLedgerMapper ledgerMapper
    ) {
        this.userMapper = userMapper;
        this.accountMapper = accountMapper;
        this.ledgerMapper = ledgerMapper;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PointsLedgerView credit(PointsMutationCommand command) {
        ValidMutation mutation = validateMutation(command, CREDIT_TYPES);
        return mutate(mutation, PointsDirection.CREDIT);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PointsLedgerView debit(PointsMutationCommand command) {
        ValidMutation mutation = validateMutation(command, Set.of(PointsBusinessType.REDEMPTION));
        return mutate(mutation, PointsDirection.DEBIT);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PointsLedgerView reverseDebit(PointsReversalCommand command) {
        ValidReversal reversal = validateReversal(command);
        requireEnabledUser(reversal.userId());
        accountMapper.ensureAccount(reversal.userId());

        PointsLedger original = findLedger(reversal.originalBusinessType(), reversal.originalBusinessId());
        if (original == null) {
            throw error(PointsErrorCode.POINTS_LEDGER_NOT_FOUND);
        }
        if (!Objects.equals(original.getUserId(), reversal.userId())
                || original.getDirection() != PointsDirection.DEBIT
                || original.getBusinessType() != PointsBusinessType.REDEMPTION) {
            throw error(PointsErrorCode.POINTS_REVERSAL_CONFLICT);
        }

        PointsLedger existingByBusiness = findLedger(
                PointsBusinessType.REVERSAL, reversal.reversalBusinessId());
        if (existingByBusiness != null) {
            validateReversalLedger(existingByBusiness, original, reversal);
            return view(existingByBusiness);
        }
        PointsLedger existingForOriginal = findReversal(original.getId());
        if (existingForOriginal != null) {
            validateReversalLedger(existingForOriginal, original, reversal);
            return view(existingForOriginal);
        }

        if (accountMapper.creditIfNoOverflow(reversal.userId(), original.getAmount()) != 1) {
            throw error(PointsErrorCode.POINTS_AMOUNT_INVALID);
        }
        PointsAccount account = requireAccount(reversal.userId());
        PointsLedger ledger = new PointsLedger();
        ledger.setUserId(reversal.userId());
        ledger.setBusinessType(PointsBusinessType.REVERSAL);
        ledger.setBusinessId(reversal.reversalBusinessId());
        ledger.setDirection(PointsDirection.CREDIT);
        ledger.setAmount(original.getAmount());
        ledger.setBalanceAfter(account.getBalance());
        ledger.setReversalOfLedgerId(original.getId());
        ledger.setRemark(reversal.remark());

        if (ledgerMapper.claim(ledger) == 1) {
            return view(requireLedger(PointsBusinessType.REVERSAL, reversal.reversalBusinessId()));
        }

        PointsLedger winner = findLedger(PointsBusinessType.REVERSAL, reversal.reversalBusinessId());
        if (winner == null) {
            winner = findReversal(original.getId());
        }
        validateReversalLedger(winner, original, reversal);
        markRollbackOnly();
        return view(winner);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PointsLedgerView adjust(AdminPointsAdjustmentCommand command) {
        if (command == null || command.delta() == null
                || command.delta() == 0L || command.delta() == Long.MIN_VALUE) {
            throw error(PointsErrorCode.POINTS_AMOUNT_INVALID);
        }
        PointsDirection direction = command.delta() > 0
                ? PointsDirection.CREDIT : PointsDirection.DEBIT;
        long amount;
        try {
            amount = direction == PointsDirection.CREDIT
                    ? command.delta() : Math.negateExact(command.delta());
        } catch (ArithmeticException exception) {
            throw error(PointsErrorCode.POINTS_AMOUNT_INVALID);
        }
        ValidMutation mutation = validateMutation(new PointsMutationCommand(
                        command.userId(), PointsBusinessType.MANUAL_ADJUSTMENT,
                        command.businessId(), amount, command.reason()),
                Set.of(PointsBusinessType.MANUAL_ADJUSTMENT));
        return mutate(mutation, direction);
    }

    @Override
    public PointsAccountView get(long userId) {
        requireEnabledUser(userId);
        PointsAccount account = findAccount(userId);
        if (account == null) {
            return new PointsAccountView(userId, 0L, null);
        }
        return accountView(account);
    }

    private PointsLedgerView mutate(ValidMutation mutation, PointsDirection direction) {
        requireEnabledUser(mutation.userId());
        accountMapper.ensureAccount(mutation.userId());
        PointsLedger existing = findLedger(mutation.businessType(), mutation.businessId());
        if (existing != null) {
            validateLedger(existing, mutation, direction);
            return view(existing);
        }

        int updated = direction == PointsDirection.CREDIT
                ? accountMapper.creditIfNoOverflow(mutation.userId(), mutation.amount())
                : accountMapper.debitIfSufficient(mutation.userId(), mutation.amount());
        if (updated != 1) {
            throw error(direction == PointsDirection.CREDIT
                    ? PointsErrorCode.POINTS_AMOUNT_INVALID
                    : PointsErrorCode.POINTS_INSUFFICIENT);
        }

        PointsAccount account = requireAccount(mutation.userId());
        PointsLedger ledger = new PointsLedger();
        ledger.setUserId(mutation.userId());
        ledger.setBusinessType(mutation.businessType());
        ledger.setBusinessId(mutation.businessId());
        ledger.setDirection(direction);
        ledger.setAmount(mutation.amount());
        ledger.setBalanceAfter(account.getBalance());
        ledger.setRemark(mutation.remark());

        if (ledgerMapper.claim(ledger) == 1) {
            return view(requireLedger(mutation.businessType(), mutation.businessId()));
        }

        PointsLedger winner = requireLedger(mutation.businessType(), mutation.businessId());
        validateLedger(winner, mutation, direction);
        markRollbackOnly();
        return view(winner);
    }

    private ValidMutation validateMutation(
            PointsMutationCommand command,
            Set<PointsBusinessType> allowedTypes
    ) {
        if (command == null || command.userId() == null || command.userId() <= 0
                || command.businessType() == null || !allowedTypes.contains(command.businessType())
                || command.amount() == null || command.amount() <= 0) {
            throw error(PointsErrorCode.POINTS_AMOUNT_INVALID);
        }
        return new ValidMutation(
                command.userId(), command.businessType(),
                normalizeRequired(command.businessId(), MAX_BUSINESS_ID_LENGTH),
                command.amount(), normalizeOptional(command.remark(), MAX_REMARK_LENGTH)
        );
    }

    private ValidReversal validateReversal(PointsReversalCommand command) {
        if (command == null || command.userId() == null || command.userId() <= 0
                || command.originalBusinessType() != PointsBusinessType.REDEMPTION) {
            throw error(PointsErrorCode.POINTS_REVERSAL_CONFLICT);
        }
        return new ValidReversal(
                command.userId(), command.originalBusinessType(),
                normalizeRequired(command.originalBusinessId(), MAX_BUSINESS_ID_LENGTH),
                normalizeRequired(command.reversalBusinessId(), MAX_BUSINESS_ID_LENGTH),
                normalizeOptional(command.remark(), MAX_REMARK_LENGTH)
        );
    }

    private void requireEnabledUser(long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || !Integer.valueOf(ENABLED).equals(user.getStatus())) {
            throw error(PointsErrorCode.POINTS_USER_UNAVAILABLE);
        }
    }

    private PointsAccount findAccount(long userId) {
        return accountMapper.selectOne(new LambdaQueryWrapper<PointsAccount>()
                .eq(PointsAccount::getUserId, userId));
    }

    private PointsAccount requireAccount(long userId) {
        PointsAccount account = findAccount(userId);
        if (account == null) {
            throw error(PointsErrorCode.POINTS_AMOUNT_INVALID);
        }
        return account;
    }

    private PointsLedger findLedger(PointsBusinessType businessType, String businessId) {
        return ledgerMapper.selectOne(new LambdaQueryWrapper<PointsLedger>()
                .eq(PointsLedger::getBusinessType, businessType)
                .eq(PointsLedger::getBusinessId, businessId));
    }

    private PointsLedger requireLedger(PointsBusinessType businessType, String businessId) {
        PointsLedger ledger = findLedger(businessType, businessId);
        if (ledger == null) {
            throw error(PointsErrorCode.POINTS_IDEMPOTENCY_CONFLICT);
        }
        return ledger;
    }

    private PointsLedger findReversal(long originalLedgerId) {
        return ledgerMapper.selectOne(new LambdaQueryWrapper<PointsLedger>()
                .eq(PointsLedger::getReversalOfLedgerId, originalLedgerId));
    }

    private void validateLedger(
            PointsLedger ledger,
            ValidMutation mutation,
            PointsDirection direction
    ) {
        if (!Objects.equals(ledger.getUserId(), mutation.userId())
                || ledger.getBusinessType() != mutation.businessType()
                || !Objects.equals(ledger.getBusinessId(), mutation.businessId())
                || ledger.getDirection() != direction
                || !Objects.equals(ledger.getAmount(), mutation.amount())
                || ledger.getReversalOfLedgerId() != null) {
            throw error(PointsErrorCode.POINTS_IDEMPOTENCY_CONFLICT);
        }
    }

    private void validateReversalLedger(
            PointsLedger ledger,
            PointsLedger original,
            ValidReversal reversal
    ) {
        if (ledger == null
                || !Objects.equals(ledger.getUserId(), reversal.userId())
                || ledger.getBusinessType() != PointsBusinessType.REVERSAL
                || !Objects.equals(ledger.getBusinessId(), reversal.reversalBusinessId())
                || ledger.getDirection() != PointsDirection.CREDIT
                || !Objects.equals(ledger.getAmount(), original.getAmount())
                || !Objects.equals(ledger.getReversalOfLedgerId(), original.getId())) {
            throw error(PointsErrorCode.POINTS_REVERSAL_CONFLICT);
        }
    }

    private String normalizeRequired(String value, int maxLength) {
        if (value == null) {
            throw error(PointsErrorCode.POINTS_AMOUNT_INVALID);
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw error(PointsErrorCode.POINTS_AMOUNT_INVALID);
        }
        return normalized;
    }

    private String normalizeOptional(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw error(PointsErrorCode.POINTS_AMOUNT_INVALID);
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private PointsAccountView accountView(PointsAccount account) {
        return new PointsAccountView(
                account.getUserId(), account.getBalance(), account.getUpdatedAt());
    }

    private PointsLedgerView view(PointsLedger ledger) {
        return new PointsLedgerView(
                ledger.getId(), ledger.getUserId(), ledger.getBusinessType(),
                ledger.getBusinessId(), ledger.getDirection(), ledger.getAmount(),
                ledger.getBalanceAfter(), ledger.getReversalOfLedgerId(), ledger.getRemark(),
                ledger.getCreatedAt()
        );
    }

    private void markRollbackOnly() {
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
    }

    private BusinessException error(PointsErrorCode errorCode) {
        return new BusinessException(errorCode);
    }

    private record ValidMutation(
            long userId,
            PointsBusinessType businessType,
            String businessId,
            long amount,
            String remark
    ) {
    }

    private record ValidReversal(
            long userId,
            PointsBusinessType originalBusinessType,
            String originalBusinessId,
            String reversalBusinessId,
            String remark
    ) {
    }
}
