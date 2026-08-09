package com.dongqh.luckyhub.points.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dongqh.luckyhub.catalog.model.RedeemableSkuSnapshot;
import com.dongqh.luckyhub.catalog.enums.ProductType;
import com.dongqh.luckyhub.catalog.service.CatalogService;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.common.exception.ForbiddenException;
import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.inventory.channel.dto.ReserveChannelStockCommand;
import com.dongqh.luckyhub.inventory.channel.service.ChannelInventoryService;
import com.dongqh.luckyhub.points.dto.CreatePointsRedemptionCommand;
import com.dongqh.luckyhub.points.dto.PointsMutationCommand;
import com.dongqh.luckyhub.points.dto.PointsRedemptionQuery;
import com.dongqh.luckyhub.points.dto.PointsReversalCommand;
import com.dongqh.luckyhub.points.dto.ReversePointsRedemptionCommand;
import com.dongqh.luckyhub.points.entity.PointsRedemptionOrder;
import com.dongqh.luckyhub.points.enums.PointsBusinessType;
import com.dongqh.luckyhub.points.enums.PointsErrorCode;
import com.dongqh.luckyhub.points.enums.PointsRedemptionStatus;
import com.dongqh.luckyhub.points.mapper.PointsRedemptionOrderMapper;
import com.dongqh.luckyhub.points.service.PointsAccountService;
import com.dongqh.luckyhub.points.service.PointsRedemptionService;
import com.dongqh.luckyhub.points.vo.PointsRedemptionView;
import com.dongqh.luckyhub.shipping.entity.ShippingAddressSnapshot;
import com.dongqh.luckyhub.shipping.enums.ShippingErrorCode;
import com.dongqh.luckyhub.shipping.enums.ShippingSourceType;
import com.dongqh.luckyhub.shipping.service.ShippingAddressSnapshotService;
import com.dongqh.luckyhub.shipping.vo.ShippingAddressSnapshotView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class PointsRedemptionServiceImpl implements PointsRedemptionService {

    private static final String POINTS_CHANNEL = "POINTS";

    private final CatalogService catalogService;
    private final PointsAccountService accountService;
    private final ChannelInventoryService inventoryService;
    private final PointsRedemptionOrderMapper orderMapper;
    private final ShippingAddressSnapshotService snapshotService;

    public PointsRedemptionServiceImpl(
            CatalogService catalogService,
            PointsAccountService accountService,
            ChannelInventoryService inventoryService,
            PointsRedemptionOrderMapper orderMapper,
            ShippingAddressSnapshotService snapshotService
    ) {
        this.catalogService = catalogService;
        this.accountService = accountService;
        this.inventoryService = inventoryService;
        this.orderMapper = orderMapper;
        this.snapshotService = snapshotService;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PointsRedemptionView create(long userId, CreatePointsRedemptionCommand command) {
        String redemptionNo = command.redemptionNo().trim();
        PointsRedemptionOrder existing = find(redemptionNo);
        if (existing != null) {
            validateIdentity(existing, userId, command.skuId(), command.quantity(), command.addressId());
            return view(existing);
        }

        RedeemableSkuSnapshot sku = catalogService.findRedeemableSku(command.skuId())
                .orElseThrow(() -> error(PointsErrorCode.REDEMPTION_SKU_UNAVAILABLE));
        validateAddressShape(sku.productType(), command.addressId());
        long totalPoints;
        try {
            totalPoints = Math.multiplyExact(sku.pointsPrice(), command.quantity().longValue());
        } catch (ArithmeticException exception) {
            throw error(PointsErrorCode.POINTS_AMOUNT_INVALID);
        }

        PointsRedemptionOrder order = newOrder(
                redemptionNo, userId, command.quantity(), sku, totalPoints);
        if (orderMapper.claim(order) != 1) {
            PointsRedemptionOrder winner = require(redemptionNo);
            validateIdentity(winner, userId, command.skuId(), command.quantity(), command.addressId());
            return view(winner);
        }

        if (command.addressId() != null) {
            ShippingAddressSnapshot snapshot = snapshotService.create(userId, command.addressId(),
                    ShippingSourceType.POINTS_REDEMPTION, String.valueOf(order.getId()));
            if (orderMapper.attachAddressSnapshot(order.getId(), snapshot.getId()) != 1) {
                throw shippingError(ShippingErrorCode.SHIPPING_IDEMPOTENCY_CONFLICT);
            }
            order.setAddressSnapshotId(snapshot.getId());
        }

        inventoryService.reserve(new ReserveChannelStockCommand(
                sku.skuId(), POINTS_CHANNEL, command.quantity(), redemptionNo));
        accountService.debit(new PointsMutationCommand(
                userId, PointsBusinessType.REDEMPTION, redemptionNo,
                totalPoints, "积分兑换商品"));
        inventoryService.confirm(redemptionNo);
        if (orderMapper.completeProcessing(redemptionNo) != 1) {
            throw error(PointsErrorCode.REDEMPTION_STATE_CONFLICT);
        }
        return view(require(redemptionNo));
    }

    @Override
    public PointsRedemptionView get(long userId, String rawRedemptionNo) {
        PointsRedemptionOrder order = require(rawRedemptionNo.trim());
        if (!Objects.equals(order.getUserId(), userId)) {
            throw new ForbiddenException();
        }
        return view(order);
    }

    @Override
    public PageResponse<PointsRedemptionView> page(long userId, PointsRedemptionQuery query) {
        LambdaQueryWrapper<PointsRedemptionOrder> wrapper =
                new LambdaQueryWrapper<PointsRedemptionOrder>()
                        .eq(PointsRedemptionOrder::getUserId, userId)
                        .eq(query.getStatus() != null,
                                PointsRedemptionOrder::getStatus, query.getStatus())
                        .orderByDesc(PointsRedemptionOrder::getCreatedAt)
                        .orderByDesc(PointsRedemptionOrder::getId);
        Page<PointsRedemptionOrder> result = orderMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);
        List<PointsRedemptionView> records = result.getRecords().stream().map(this::view).toList();
        return new PageResponse<>(records, result.getTotal(), result.getCurrent(),
                result.getSize(), result.getPages());
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PointsRedemptionView reverse(
            String rawRedemptionNo,
            ReversePointsRedemptionCommand command
    ) {
        String redemptionNo = rawRedemptionNo.trim();
        String reversalNo = command.reversalNo().trim();
        String reason = command.reason().trim();
        PointsRedemptionOrder order = orderMapper.lockByRedemptionNo(redemptionNo);
        if (order == null) {
            throw error(PointsErrorCode.REDEMPTION_NOT_FOUND);
        }
        if (order.getStatus() == PointsRedemptionStatus.REVERSED) {
            if (!Objects.equals(order.getReversalNo(), reversalNo)) {
                throw error(PointsErrorCode.REDEMPTION_STATE_CONFLICT);
            }
            return view(order);
        }
        if (order.getStatus() != PointsRedemptionStatus.COMPLETED) {
            throw error(PointsErrorCode.REDEMPTION_STATE_CONFLICT);
        }

        try {
            accountService.reverseDebit(new PointsReversalCommand(
                    order.getUserId(), PointsBusinessType.REDEMPTION,
                    redemptionNo, reversalNo, reason));
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == PointsErrorCode.POINTS_REVERSAL_CONFLICT) {
                throw error(PointsErrorCode.REDEMPTION_STATE_CONFLICT);
            }
            throw exception;
        }
        inventoryService.reverseConfirmed(redemptionNo);
        if (orderMapper.reverseCompleted(redemptionNo, reversalNo, reason) != 1) {
            throw error(PointsErrorCode.REDEMPTION_STATE_CONFLICT);
        }
        return view(require(redemptionNo));
    }

    private PointsRedemptionOrder newOrder(
            String redemptionNo,
            long userId,
            int quantity,
            RedeemableSkuSnapshot sku,
            long totalPoints
    ) {
        PointsRedemptionOrder order = new PointsRedemptionOrder();
        order.setRedemptionNo(redemptionNo);
        order.setUserId(userId);
        order.setSkuId(sku.skuId());
        order.setQuantity(quantity);
        order.setUnitPoints(sku.pointsPrice());
        order.setTotalPoints(totalPoints);
        order.setProductCode(sku.productCode());
        order.setProductName(sku.productName());
        order.setSkuCode(sku.skuCode());
        order.setSkuName(sku.skuName());
        order.setProductType(sku.productType());
        order.setImageUrl(sku.imageUrl());
        order.setStatus(PointsRedemptionStatus.PROCESSING);
        return order;
    }

    private PointsRedemptionOrder find(String redemptionNo) {
        return orderMapper.selectOne(new LambdaQueryWrapper<PointsRedemptionOrder>()
                .eq(PointsRedemptionOrder::getRedemptionNo, redemptionNo));
    }

    private PointsRedemptionOrder require(String redemptionNo) {
        PointsRedemptionOrder order = find(redemptionNo);
        if (order == null) {
            throw error(PointsErrorCode.REDEMPTION_NOT_FOUND);
        }
        return order;
    }

    private void validateIdentity(
            PointsRedemptionOrder order,
            long userId,
            long skuId,
            int quantity,
            Long addressId
    ) {
        if (!Objects.equals(order.getUserId(), userId)
                || !Objects.equals(order.getSkuId(), skuId)
                || !Objects.equals(order.getQuantity(), quantity)
                || !sameAddress(order, addressId)) {
            throw error(PointsErrorCode.POINTS_IDEMPOTENCY_CONFLICT);
        }
    }

    private PointsRedemptionView view(PointsRedemptionOrder order) {
        return new PointsRedemptionView(
                order.getId(), order.getRedemptionNo(), order.getUserId(), order.getSkuId(),
                order.getQuantity(), order.getUnitPoints(), order.getTotalPoints(),
                order.getProductCode(), order.getProductName(), order.getSkuCode(),
                order.getSkuName(), order.getProductType(), order.getImageUrl(),
                order.getStatus(), order.getReversalNo(), order.getFailureReason(),
                snapshotView(order.getAddressSnapshotId()), order.getShippingOrderId(),
                order.getCreatedAt(), order.getUpdatedAt());
    }

    private void validateAddressShape(ProductType productType, Long addressId) {
        if ((productType == ProductType.PHYSICAL) != (addressId != null)) {
            throw shippingError(ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        }
    }

    private boolean sameAddress(PointsRedemptionOrder order, Long addressId) {
        if (order.getAddressSnapshotId() == null) {
            return addressId == null;
        }
        return addressId != null
                && Objects.equals(snapshotService.require(order.getAddressSnapshotId()).getAddressId(), addressId);
    }

    private ShippingAddressSnapshotView snapshotView(Long snapshotId) {
        if (snapshotId == null) {
            return null;
        }
        ShippingAddressSnapshot snapshot = snapshotService.require(snapshotId);
        return new ShippingAddressSnapshotView(snapshot.getId(), snapshot.getSnapshotNo(),
                snapshot.getReceiverMasked(), snapshot.getPhoneMasked(), snapshot.getRegionMasked());
    }

    private BusinessException error(PointsErrorCode errorCode) {
        return new BusinessException(errorCode);
    }

    private BusinessException shippingError(ShippingErrorCode errorCode) {
        return new BusinessException(errorCode);
    }
}
