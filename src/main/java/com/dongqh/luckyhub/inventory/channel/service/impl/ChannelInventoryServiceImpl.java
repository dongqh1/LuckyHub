package com.dongqh.luckyhub.inventory.channel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dongqh.luckyhub.catalog.entity.ProductSku;
import com.dongqh.luckyhub.catalog.mapper.ProductSkuMapper;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.inventory.channel.dto.AllocateChannelStockCommand;
import com.dongqh.luckyhub.inventory.channel.dto.InitializeSkuStockCommand;
import com.dongqh.luckyhub.inventory.channel.dto.ReserveChannelStockCommand;
import com.dongqh.luckyhub.inventory.channel.entity.InventoryChannelStock;
import com.dongqh.luckyhub.inventory.channel.entity.InventoryLedger;
import com.dongqh.luckyhub.inventory.channel.entity.InventoryReservation;
import com.dongqh.luckyhub.inventory.channel.entity.SkuInventory;
import com.dongqh.luckyhub.inventory.channel.enums.ChannelInventoryErrorCode;
import com.dongqh.luckyhub.inventory.channel.enums.InventoryOperation;
import com.dongqh.luckyhub.inventory.channel.enums.InventoryReservationStatus;
import com.dongqh.luckyhub.inventory.channel.mapper.ChannelInventoryMapper;
import com.dongqh.luckyhub.inventory.channel.mapper.InventoryLedgerMapper;
import com.dongqh.luckyhub.inventory.channel.mapper.InventoryReservationMapper;
import com.dongqh.luckyhub.inventory.channel.mapper.SkuInventoryMapper;
import com.dongqh.luckyhub.inventory.channel.service.ChannelInventoryService;
import com.dongqh.luckyhub.inventory.channel.vo.ChannelInventoryView;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.util.Locale;
import java.util.Objects;

@Service
public class ChannelInventoryServiceImpl implements ChannelInventoryService {

    private static final int ENABLED = 1;

    private final ProductSkuMapper skuMapper;
    private final SkuInventoryMapper totalMapper;
    private final ChannelInventoryMapper channelMapper;
    private final InventoryReservationMapper reservationMapper;
    private final InventoryLedgerMapper ledgerMapper;

    public ChannelInventoryServiceImpl(
            ProductSkuMapper skuMapper,
            SkuInventoryMapper totalMapper,
            ChannelInventoryMapper channelMapper,
            InventoryReservationMapper reservationMapper,
            InventoryLedgerMapper ledgerMapper
    ) {
        this.skuMapper = skuMapper;
        this.totalMapper = totalMapper;
        this.channelMapper = channelMapper;
        this.reservationMapper = reservationMapper;
        this.ledgerMapper = ledgerMapper;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ChannelInventoryView initialize(InitializeSkuStockCommand command) {
        ProductSku sku = skuMapper.selectById(command.skuId());
        if (sku == null || !Integer.valueOf(ENABLED).equals(sku.getStatus())) {
            throw error(ChannelInventoryErrorCode.INVENTORY_SKU_UNAVAILABLE);
        }
        String businessNo = command.businessNo().trim();
        if (!claimLedger(businessNo, command.skuId(), null,
                InventoryOperation.INITIALIZE, command.totalStock())) {
            return totalView(requireTotal(command.skuId()));
        }

        SkuInventory total = new SkuInventory();
        total.setSkuId(command.skuId());
        total.setTotalStock(command.totalStock());
        total.setAllocatedStock(0);
        total.setVersion(0);
        try {
            totalMapper.insert(total);
        } catch (DuplicateKeyException exception) {
            throw error(ChannelInventoryErrorCode.INVENTORY_STATE_CONFLICT);
        }
        return totalView(total);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ChannelInventoryView allocate(AllocateChannelStockCommand command) {
        requireTotal(command.skuId());
        String channelCode = normalizeChannel(command.channelCode());
        String businessNo = command.businessNo().trim();
        if (!claimLedger(businessNo, command.skuId(), channelCode,
                InventoryOperation.ALLOCATE, command.quantity())) {
            return get(command.skuId(), channelCode);
        }
        if (totalMapper.allocateIfAvailable(command.skuId(), command.quantity()) != 1) {
            throw error(ChannelInventoryErrorCode.INVENTORY_INSUFFICIENT);
        }
        channelMapper.addAllocation(command.skuId(), channelCode, command.quantity());
        return get(command.skuId(), channelCode);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ChannelInventoryView reserve(ReserveChannelStockCommand command) {
        String channelCode = normalizeChannel(command.channelCode());
        String reservationNo = command.reservationNo().trim();
        requireChannel(command.skuId(), channelCode);
        if (!claimLedger("RESERVE:" + reservationNo, command.skuId(), channelCode,
                InventoryOperation.RESERVE, command.quantity())) {
            InventoryReservation existing = requireReservation(reservationNo);
            validateReservation(existing, command.skuId(), channelCode, command.quantity());
            return view(existing);
        }
        if (channelMapper.reserveIfAvailable(command.skuId(), channelCode, command.quantity()) != 1) {
            throw error(ChannelInventoryErrorCode.INVENTORY_INSUFFICIENT);
        }

        InventoryReservation reservation = new InventoryReservation();
        reservation.setReservationNo(reservationNo);
        reservation.setSkuId(command.skuId());
        reservation.setChannelCode(channelCode);
        reservation.setQuantity(command.quantity());
        reservation.setStatus(InventoryReservationStatus.RESERVED);
        try {
            reservationMapper.insert(reservation);
        } catch (DuplicateKeyException exception) {
            throw error(ChannelInventoryErrorCode.INVENTORY_IDEMPOTENCY_CONFLICT);
        }
        return view(reservation);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ChannelInventoryView confirm(String reservationNo) {
        return transition(reservationNo, InventoryReservationStatus.CONFIRMED, InventoryOperation.CONFIRM);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ChannelInventoryView release(String reservationNo) {
        return transition(reservationNo, InventoryReservationStatus.RELEASED, InventoryOperation.RELEASE);
    }

    @Override
    public ChannelInventoryView get(long skuId, String channelCode) {
        SkuInventory total = requireTotal(skuId);
        InventoryChannelStock channel = requireChannel(skuId, normalizeChannel(channelCode));
        return toView(total, channel, null);
    }

    private ChannelInventoryView transition(
            String rawReservationNo,
            InventoryReservationStatus targetStatus,
            InventoryOperation operation
    ) {
        String reservationNo = rawReservationNo.trim();
        InventoryReservation reservation = requireReservation(reservationNo);
        if (reservation.getStatus() == targetStatus) {
            return view(reservation);
        }
        if (reservation.getStatus() != InventoryReservationStatus.RESERVED) {
            throw error(ChannelInventoryErrorCode.INVENTORY_STATE_CONFLICT);
        }

        String businessNo = operation.name() + ":" + reservationNo;
        if (!claimLedger(businessNo, reservation.getSkuId(), reservation.getChannelCode(),
                operation, reservation.getQuantity())) {
            return view(requireReservation(reservationNo));
        }
        if (reservationMapper.transitionReserved(reservationNo, targetStatus) != 1) {
            InventoryReservation winner = requireReservation(reservationNo);
            if (winner.getStatus() == targetStatus) {
                return view(winner);
            }
            throw error(ChannelInventoryErrorCode.INVENTORY_STATE_CONFLICT);
        }

        int updated = targetStatus == InventoryReservationStatus.CONFIRMED
                ? channelMapper.confirmReserved(reservation.getSkuId(), reservation.getChannelCode(),
                reservation.getQuantity())
                : channelMapper.releaseReserved(reservation.getSkuId(), reservation.getChannelCode(),
                reservation.getQuantity());
        if (updated != 1) {
            throw error(ChannelInventoryErrorCode.INVENTORY_STATE_CONFLICT);
        }
        reservation.setStatus(targetStatus);
        return view(reservation);
    }

    private boolean claimLedger(
            String businessNo,
            long skuId,
            String channelCode,
            InventoryOperation operation,
            int quantity
    ) {
        InventoryLedger existing = findLedger(businessNo);
        if (existing != null) {
            validateLedger(existing, skuId, channelCode, operation, quantity);
            return false;
        }

        InventoryLedger ledger = new InventoryLedger();
        ledger.setBusinessNo(businessNo);
        ledger.setSkuId(skuId);
        ledger.setChannelCode(channelCode);
        ledger.setOperation(operation);
        ledger.setQuantity(quantity);
        if (ledgerMapper.claim(ledger) == 1) {
            return true;
        }
        InventoryLedger winner = findLedger(businessNo);
        validateLedger(winner, skuId, channelCode, operation, quantity);
        return false;
    }

    private void validateLedger(InventoryLedger ledger, long skuId, String channelCode,
                                InventoryOperation operation, int quantity) {
        if (ledger == null
                || !Objects.equals(ledger.getSkuId(), skuId)
                || !Objects.equals(ledger.getChannelCode(), channelCode)
                || ledger.getOperation() != operation
                || !Objects.equals(ledger.getQuantity(), quantity)) {
            throw error(ChannelInventoryErrorCode.INVENTORY_IDEMPOTENCY_CONFLICT);
        }
    }

    private void validateReservation(InventoryReservation reservation, long skuId,
                                     String channelCode, int quantity) {
        if (!Objects.equals(reservation.getSkuId(), skuId)
                || !Objects.equals(reservation.getChannelCode(), channelCode)
                || !Objects.equals(reservation.getQuantity(), quantity)) {
            throw error(ChannelInventoryErrorCode.INVENTORY_IDEMPOTENCY_CONFLICT);
        }
    }

    private ChannelInventoryView view(InventoryReservation reservation) {
        SkuInventory total = requireTotal(reservation.getSkuId());
        InventoryChannelStock channel = requireChannel(
                reservation.getSkuId(), reservation.getChannelCode());
        return toView(total, channel, reservation);
    }

    private ChannelInventoryView totalView(SkuInventory total) {
        return new ChannelInventoryView(total.getSkuId(), null, total.getTotalStock(),
                total.getAllocatedStock(), null, null, null, null, null);
    }

    private ChannelInventoryView toView(SkuInventory total, InventoryChannelStock channel,
                                        InventoryReservation reservation) {
        return new ChannelInventoryView(total.getSkuId(), channel.getChannelCode(), total.getTotalStock(),
                channel.getAllocatedStock(), channel.getAvailableStock(), channel.getReservedStock(),
                channel.getConsumedStock(), reservation == null ? null : reservation.getReservationNo(),
                reservation == null ? null : reservation.getStatus());
    }

    private SkuInventory requireTotal(long skuId) {
        SkuInventory total = totalMapper.selectOne(
                new LambdaQueryWrapper<SkuInventory>().eq(SkuInventory::getSkuId, skuId));
        if (total == null) {
            throw error(ChannelInventoryErrorCode.INVENTORY_NOT_FOUND);
        }
        return total;
    }

    private InventoryChannelStock requireChannel(long skuId, String channelCode) {
        InventoryChannelStock channel = channelMapper.selectOne(
                new LambdaQueryWrapper<InventoryChannelStock>()
                        .eq(InventoryChannelStock::getSkuId, skuId)
                        .eq(InventoryChannelStock::getChannelCode, channelCode));
        if (channel == null) {
            throw error(ChannelInventoryErrorCode.INVENTORY_NOT_FOUND);
        }
        return channel;
    }

    private InventoryReservation requireReservation(String reservationNo) {
        InventoryReservation reservation = reservationMapper.selectOne(
                new LambdaQueryWrapper<InventoryReservation>()
                        .eq(InventoryReservation::getReservationNo, reservationNo));
        if (reservation == null) {
            throw error(ChannelInventoryErrorCode.INVENTORY_NOT_FOUND);
        }
        return reservation;
    }

    private InventoryLedger findLedger(String businessNo) {
        return ledgerMapper.selectOne(new LambdaQueryWrapper<InventoryLedger>()
                .eq(InventoryLedger::getBusinessNo, businessNo));
    }

    private String normalizeChannel(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private BusinessException error(ChannelInventoryErrorCode errorCode) {
        return new BusinessException(errorCode);
    }
}
