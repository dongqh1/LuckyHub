package com.dongqh.luckyhub.drawchance.service.impl;

import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.drawchance.dto.DrawChanceReservationCommand;
import com.dongqh.luckyhub.drawchance.entity.DrawChanceAccount;
import com.dongqh.luckyhub.drawchance.entity.DrawChanceLedger;
import com.dongqh.luckyhub.drawchance.entity.DrawChanceReservation;
import com.dongqh.luckyhub.drawchance.enums.DrawChanceBusinessType;
import com.dongqh.luckyhub.drawchance.enums.DrawChanceDirection;
import com.dongqh.luckyhub.drawchance.enums.DrawChanceErrorCode;
import com.dongqh.luckyhub.drawchance.enums.DrawChanceReservationStatus;
import com.dongqh.luckyhub.drawchance.mapper.DrawChanceAccountMapper;
import com.dongqh.luckyhub.drawchance.mapper.DrawChanceLedgerMapper;
import com.dongqh.luckyhub.drawchance.mapper.DrawChanceReservationMapper;
import com.dongqh.luckyhub.drawchance.model.DrawChanceReservationResult;
import com.dongqh.luckyhub.drawchance.service.DrawChanceService;
import com.dongqh.luckyhub.drawchance.vo.DrawChanceAccountView;
import com.dongqh.luckyhub.lottery.entity.LotteryDrawOrder;
import com.dongqh.luckyhub.lottery.enums.DrawOrderStatus;
import com.dongqh.luckyhub.lottery.mapper.LotteryDrawOrderMapper;
import com.dongqh.luckyhub.rbac.entity.SysUser;
import com.dongqh.luckyhub.rbac.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class DrawChanceServiceImpl implements DrawChanceService {
    private final DrawChanceAccountMapper accounts;
    private final DrawChanceLedgerMapper ledgers;
    private final DrawChanceReservationMapper reservations;
    private final SysUserMapper users;
    private final LotteryDrawOrderMapper orders;

    public DrawChanceServiceImpl(DrawChanceAccountMapper accounts, DrawChanceLedgerMapper ledgers,
                                 DrawChanceReservationMapper reservations, SysUserMapper users,
                                 LotteryDrawOrderMapper orders) {
        this.accounts = accounts;
        this.ledgers = ledgers;
        this.reservations = reservations;
        this.users = users;
        this.orders = orders;
    }

    @Override
    @Transactional
    public DrawChanceAccountView credit(long userId, String businessId, long chances) {
        validateBusiness(userId, businessId, chances);
        SysUser user = users.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(DrawChanceErrorCode.USER_NOT_AVAILABLE);
        }
        DrawChanceAccount account = lockAccount(userId);
        DrawChanceLedger existing = ledgers.selectBusiness(DrawChanceBusinessType.LOTTERY_REWARD, businessId);
        if (existing != null) {
            if (!Objects.equals(existing.getUserId(), userId) || !Objects.equals(existing.getAmount(), chances)
                    || existing.getDirection() != DrawChanceDirection.CREDIT) {
                throw new BusinessException(DrawChanceErrorCode.IDENTITY_CONFLICT);
            }
            return view(account);
        }
        long available = exactAdd(account.getAvailableBalance(), chances);
        updateAccount(account, available, account.getReservedBalance());
        insertLedger(userId, DrawChanceBusinessType.LOTTERY_REWARD, businessId,
                DrawChanceDirection.CREDIT, chances, available, account.getReservedBalance());
        return new DrawChanceAccountView(userId, available, account.getReservedBalance());
    }

    @Override
    @Transactional
    public DrawChanceReservationResult reserve(DrawChanceReservationCommand command) {
        DrawChanceReservation candidate = new DrawChanceReservation();
        candidate.setRequestId(command.requestId());
        candidate.setUserId(command.userId());
        candidate.setActivityId(command.activityId());
        candidate.setDrawDate(command.drawDate());
        candidate.setDrawCount(command.drawCount());
        int inserted = reservations.insertIfAbsent(candidate);
        DrawChanceReservation reservation = reservations.lockByRequestId(command.requestId());
        requireSameIdentity(reservation, command);
        if (inserted == 0) return result(reservation, true);

        DrawChanceAccount account = lockAccount(command.userId());
        long bonus = Math.min(account.getAvailableBalance(), command.drawCount());
        if (bonus > 0) {
            updateAccount(account, account.getAvailableBalance() - bonus,
                    exactAdd(account.getReservedBalance(), bonus));
            if (reservations.setBonus(reservation.getId(), bonus) != 1) throw balanceError();
            reservation.setBonusReserved(bonus);
        }
        return result(reservation, false);
    }

    @Override
    @Transactional
    public void confirm(String requestId) {
        settle(requestId, DrawChanceReservationStatus.CONFIRMED);
    }

    @Override
    @Transactional
    public void release(String requestId) {
        settle(requestId, DrawChanceReservationStatus.RELEASED);
    }

    @Override
    @Transactional
    public int reconcileExpired(int limit, LocalDateTime cutoff) {
        if (limit <= 0 || limit > 1000 || cutoff == null) {
            throw new BusinessException(DrawChanceErrorCode.INVALID_REQUEST);
        }
        int settled = 0;
        for (DrawChanceReservation reservation : reservations.lockExpired(limit, cutoff)) {
            LotteryDrawOrder order = orders.selectByRequestIdForUpdate(reservation.getRequestId());
            if (order != null && order.getStatus() == DrawOrderStatus.SUCCESS) {
                settleLocked(reservation, DrawChanceReservationStatus.CONFIRMED);
                settled++;
            } else if (order != null && order.getStatus() == DrawOrderStatus.FAILED) {
                settleLocked(reservation, DrawChanceReservationStatus.RELEASED);
                settled++;
            }
        }
        return settled;
    }

    @Override
    public DrawChanceAccountView get(long userId) {
        if (userId <= 0) throw new BusinessException(DrawChanceErrorCode.INVALID_REQUEST);
        DrawChanceAccount account = accounts.selectByUserId(userId);
        return account == null ? new DrawChanceAccountView(userId, 0, 0) : view(account);
    }

    private void settle(String requestId, DrawChanceReservationStatus target) {
        if (requestId == null || requestId.isBlank() || requestId.length() > 64) {
            throw new BusinessException(DrawChanceErrorCode.INVALID_REQUEST);
        }
        DrawChanceReservation reservation = reservations.lockByRequestId(requestId);
        if (reservation == null) throw new BusinessException(DrawChanceErrorCode.RESERVATION_NOT_FOUND);
        if (reservation.getStatus() == target) return;
        if (reservation.getStatus() != DrawChanceReservationStatus.RESERVED) {
            throw new BusinessException(DrawChanceErrorCode.TERMINAL_STATE_CONFLICT);
        }
        settleLocked(reservation, target);
    }

    private void settleLocked(DrawChanceReservation reservation, DrawChanceReservationStatus target) {
        long bonus = reservation.getBonusReserved();
        if (bonus > 0) {
            DrawChanceAccount account = lockAccount(reservation.getUserId());
            if (account.getReservedBalance() < bonus) throw balanceError();
            long available = account.getAvailableBalance();
            DrawChanceBusinessType type;
            DrawChanceDirection direction;
            if (target == DrawChanceReservationStatus.RELEASED) {
                available = exactAdd(available, bonus);
                type = DrawChanceBusinessType.DRAW_RELEASE;
                direction = DrawChanceDirection.CREDIT;
            } else {
                type = DrawChanceBusinessType.DRAW_CONSUME;
                direction = DrawChanceDirection.DEBIT;
            }
            long reserved = account.getReservedBalance() - bonus;
            updateAccount(account, available, reserved);
            insertLedger(reservation.getUserId(), type, reservation.getRequestId(), direction,
                    bonus, available, reserved);
        }
        if (reservations.settle(reservation.getId(), target) != 1) throw balanceError();
        reservation.setStatus(target);
    }

    private DrawChanceReservationResult result(DrawChanceReservation reservation, boolean duplicate) {
        long cumulative = reservations.sumActiveBonus(reservation.getUserId(),
                reservation.getActivityId(), reservation.getDrawDate());
        return new DrawChanceReservationResult(reservation.getRequestId(), reservation.getActivityId(),
                reservation.getUserId(), reservation.getDrawCount(), reservation.getDrawDate(),
                reservation.getBonusReserved(), cumulative, reservation.getStatus(), duplicate);
    }

    private void requireSameIdentity(DrawChanceReservation reservation, DrawChanceReservationCommand command) {
        if (reservation == null || !Objects.equals(reservation.getUserId(), command.userId())
                || !Objects.equals(reservation.getActivityId(), command.activityId())
                || !Objects.equals(reservation.getDrawCount(), command.drawCount())
                || !Objects.equals(reservation.getDrawDate(), command.drawDate())) {
            throw new BusinessException(DrawChanceErrorCode.IDENTITY_CONFLICT);
        }
    }

    private DrawChanceAccount lockAccount(long userId) {
        accounts.insertIfAbsent(userId);
        DrawChanceAccount account = accounts.lockByUserId(userId);
        if (account == null) throw balanceError();
        return account;
    }

    private void updateAccount(DrawChanceAccount account, long available, long reserved) {
        if (available < 0 || reserved < 0 || accounts.updateBalances(account.getId(), available, reserved) != 1) {
            throw balanceError();
        }
        account.setAvailableBalance(available);
        account.setReservedBalance(reserved);
    }

    private void insertLedger(long userId, DrawChanceBusinessType type, String businessId,
                              DrawChanceDirection direction, long amount,
                              long available, long reserved) {
        DrawChanceLedger ledger = new DrawChanceLedger();
        ledger.setUserId(userId); ledger.setBusinessType(type); ledger.setBusinessId(businessId);
        ledger.setDirection(direction); ledger.setAmount(amount);
        ledger.setAvailableAfter(available); ledger.setReservedAfter(reserved);
        ledgers.insert(ledger);
    }

    private void validateBusiness(long userId, String businessId, long chances) {
        if (userId <= 0 || businessId == null || businessId.isBlank()
                || businessId.length() > 100 || chances <= 0) {
            throw new BusinessException(DrawChanceErrorCode.INVALID_REQUEST);
        }
    }

    private long exactAdd(long left, long right) {
        try { return Math.addExact(left, right); }
        catch (ArithmeticException exception) { throw balanceError(); }
    }

    private DrawChanceAccountView view(DrawChanceAccount account) {
        return new DrawChanceAccountView(account.getUserId(), account.getAvailableBalance(),
                account.getReservedBalance());
    }

    private BusinessException balanceError() {
        return new BusinessException(DrawChanceErrorCode.BALANCE_INVALID);
    }
}
