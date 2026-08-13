package com.dongqh.luckyhub.shipping.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.fulfillment.enums.FulfillmentStatus;
import com.dongqh.luckyhub.fulfillment.service.FulfillmentTaskService;
import com.dongqh.luckyhub.fulfillment.vo.FulfillmentTaskView;
import com.dongqh.luckyhub.shipping.dto.ShippingOrderQuery;
import com.dongqh.luckyhub.shipping.entity.ShippingAddressSnapshot;
import com.dongqh.luckyhub.shipping.entity.ShippingOrder;
import com.dongqh.luckyhub.shipping.enums.ShippingErrorCode;
import com.dongqh.luckyhub.shipping.enums.ShippingStatus;
import com.dongqh.luckyhub.shipping.mapper.ShippingAddressSnapshotMapper;
import com.dongqh.luckyhub.shipping.mapper.ShippingOrderMapper;
import com.dongqh.luckyhub.shipping.service.ShippingAdminService;
import com.dongqh.luckyhub.shipping.service.ShippingOrderService;
import com.dongqh.luckyhub.shipping.service.ShippingProjectionWorker;
import com.dongqh.luckyhub.shipping.vo.ShippingOrderView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class ShippingAdminServiceImpl implements ShippingAdminService {
    private static final Logger log = LoggerFactory.getLogger(ShippingAdminServiceImpl.class);
    private static final int MAX_SCAN = 100;
    private static final Pattern UNSAFE_NOTE = Pattern.compile("(?i)(?:"
            + "[\\p{Cc}\\p{Cf}\\{\\}\\[\\]]"
            + "|[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}"
            + "|(?<!\\d)1[3-9]\\d{9}(?!\\d)"
            + "|(?<!\\d)(?:\\d{17}[0-9Xx]|\\d{15})(?!\\d)"
            + "|(?<!\\d)0\\d{2,3}[- ]?\\d{7,8}(?!\\d)"
            + "|\\b(?:exception|payload|ciphertext|secret|authorization|provider\\s+response)\\b"
            + "|异常堆栈|原始响应|供应商响应|密文|明文|密钥|秘密|授权头|签名"
            + "|收件人|姓名|联系人|手机号|手机号码|电话号码|电话|座机|身份证|证件号|住址|详细地址"
            + "|(?:省|自治区|特别行政区).{0,30}(?:市|州).{0,30}(?:区|县).{0,50}(?:路|街|道|巷|弄|号|栋|单元|室)"
            + ")");
    private static final Pattern SAFE_NOTE = Pattern.compile("^(?:(?:"
            + "人工核验通过|已核对安全数据|已核对商品资料|确认可重试|可以重新发货"
            + "|用户申请终止|停止处理|重复任务|无需继续履约|隔离任务已核验|系统已恢复"
            + "|运维确认|安全原因|工单\\s*[：:#-]?\\s*[A-Za-z0-9_-]{1,32}"
            + ")[\\s，。；、,;]*)+$");

    private final ShippingOrderMapper orders;
    private final ShippingAddressSnapshotMapper snapshots;
    private final FulfillmentTaskService fulfillment;
    private final ShippingOrderService shippingOrders;
    private final ShippingProjectionWorker projectionWorker;

    public ShippingAdminServiceImpl(ShippingOrderMapper orders, ShippingAddressSnapshotMapper snapshots,
                                    FulfillmentTaskService fulfillment, ShippingOrderService shippingOrders) {
        this(orders, snapshots, fulfillment, shippingOrders, null);
    }

    @Autowired
    public ShippingAdminServiceImpl(ShippingOrderMapper orders, ShippingAddressSnapshotMapper snapshots,
                                    FulfillmentTaskService fulfillment, ShippingOrderService shippingOrders,
                                    ShippingProjectionWorker projectionWorker) {
        this.orders = orders;
        this.snapshots = snapshots;
        this.fulfillment = fulfillment;
        this.shippingOrders = shippingOrders;
        this.projectionWorker = projectionWorker;
    }

    @Override
    public PageResponse<ShippingOrderView> page(ShippingOrderQuery query) {
        Page<ShippingOrder> page = orders.selectPage(new Page<>(query.getPage(), query.getSize()),
                new LambdaQueryWrapper<ShippingOrder>()
                        .eq(query.getStatus() != null, ShippingOrder::getStatus, query.getStatus())
                        .eq(query.getSourceType() != null, ShippingOrder::getSourceType, query.getSourceType())
                        .eq(hasText(query.getSourceId()), ShippingOrder::getSourceId, trimmed(query.getSourceId()))
                        .eq(query.getTargetUserId() != null, ShippingOrder::getTargetUserId, query.getTargetUserId())
                        .eq(hasText(query.getWaybillNo()), ShippingOrder::getWaybillNo, trimmed(query.getWaybillNo()))
                        .orderByDesc(ShippingOrder::getCreatedAt).orderByDesc(ShippingOrder::getId));
        return new PageResponse<>(page.getRecords().stream().map(this::view).toList(), page.getTotal(),
                page.getCurrent(), page.getSize(), page.getPages());
    }

    @Override
    public ShippingOrderView get(String shippingNo) {
        ShippingOrder order = orders.selectByShippingNo(requiredNo(shippingNo));
        if (order == null) throw error(ShippingErrorCode.SHIPPING_NOT_FOUND);
        return view(order);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ShippingOrderView retry(String shippingNo, long operatorId, String note) {
        ShippingOrder order = lock(shippingNo);
        if (order.getStatus() != ShippingStatus.FAILED || order.getFulfillmentNo() == null) {
            throw error(ShippingErrorCode.SHIPPING_STATE_CONFLICT);
        }
        String validatedNote = safeNote(note);
        FulfillmentTaskView task = fulfillment.get(order.getFulfillmentNo());
        if (task.status() != FulfillmentStatus.QUARANTINED) {
            throw error(ShippingErrorCode.SHIPPING_STATE_CONFLICT);
        }
        fulfillment.retryQuarantined(order.getFulfillmentNo(), positiveOperator(operatorId), validatedNote);
        shippingOrders.projectFulfillmentState(order.getFulfillmentNo());
        return get(order.getShippingNo());
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ShippingOrderView terminate(String shippingNo, long operatorId, String note) {
        ShippingOrder order = lock(shippingNo);
        if (order.getFulfillmentNo() == null || order.getStatus() == ShippingStatus.SHIPPED
                || order.getStatus() == ShippingStatus.IN_TRANSIT || order.getStatus() == ShippingStatus.DELIVERED
                || order.getStatus() == ShippingStatus.TERMINATED) {
            throw error(ShippingErrorCode.SHIPPING_STATE_CONFLICT);
        }
        String validatedNote = safeNote(note);
        FulfillmentTaskView task = fulfillment.get(order.getFulfillmentNo());
        if (task.status() == FulfillmentStatus.PROCESSING || task.status() == FulfillmentStatus.SUCCEEDED
                || task.status() == FulfillmentStatus.TERMINATED) {
            throw error(ShippingErrorCode.SHIPPING_STATE_CONFLICT);
        }
        fulfillment.terminate(order.getFulfillmentNo(), positiveOperator(operatorId), validatedNote);
        shippingOrders.projectFulfillmentState(order.getFulfillmentNo());
        return get(order.getShippingNo());
    }

    @Override
    public int projectPending() {
        List<Long> candidates = orders.selectProjectionCandidateIds(MAX_SCAN);
        int projected = 0;
        for (Long id : candidates) {
            try {
                if (projectionWorker.projectOne(id)) projected++;
            } catch (RuntimeException ignored) {
                log.warn("单条物流状态投影失败 shippingOrderId={}", id);
            }
        }
        return projected;
    }

    private ShippingOrder lock(String shippingNo) {
        ShippingOrder order = orders.lockByShippingNo(requiredNo(shippingNo));
        if (order == null) throw error(ShippingErrorCode.SHIPPING_NOT_FOUND);
        return order;
    }

    private ShippingOrderView view(ShippingOrder order) {
        ShippingAddressSnapshot snapshot = snapshots.selectById(order.getAddressSnapshotId());
        return new ShippingOrderView(order.getId(), order.getShippingNo(), order.getSourceType(), order.getSourceId(),
                order.getTargetUserId(), order.getAddressSnapshotId(), order.getSkuCode(), order.getProductName(),
                order.getImageUrl(), order.getQuantity(), order.getFulfillmentNo(), order.getCarrierCode(),
                order.getCarrierName(), order.getWaybillNo(), order.getStatus(), order.getLastErrorCode(),
                order.getLastErrorMessage(), order.getShippedAt(), order.getDeliveredAt(), order.getFailedAt(),
                order.getTerminatedAt(), order.getCreatedAt(), order.getUpdatedAt(),
                snapshot == null ? null : snapshot.getReceiverMasked(),
                snapshot == null ? null : snapshot.getPhoneMasked(),
                snapshot == null ? null : snapshot.getRegionMasked());
    }

    private String requiredNo(String value) {
        String normalized = trimmed(value);
        if (normalized == null || normalized.isBlank() || normalized.length() > 64) {
            throw error(ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        }
        return normalized;
    }

    private long positiveOperator(long value) {
        if (value <= 0) throw error(ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        return value;
    }

    private String safeNote(String note) {
        String normalized = trimmed(note);
        if (normalized == null || normalized.isBlank()) return null;
        if (UNSAFE_NOTE.matcher(normalized).find() || !SAFE_NOTE.matcher(normalized).matches()) {
            throw error(ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        }
        return normalized.substring(0, Math.min(500, normalized.length()));
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private String trimmed(String value) { return value == null ? null : value.trim(); }
    private BusinessException error(ShippingErrorCode code) { return new BusinessException(code); }
}
