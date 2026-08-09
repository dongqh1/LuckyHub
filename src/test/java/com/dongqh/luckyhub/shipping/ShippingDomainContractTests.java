package com.dongqh.luckyhub.shipping;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.luckyhub.benefit.entity.UserBenefit;
import com.dongqh.luckyhub.benefit.enums.BenefitStatus;
import com.dongqh.luckyhub.common.enums.ErrorCode;
import com.dongqh.luckyhub.order.entity.MallOrder;
import com.dongqh.luckyhub.points.entity.PointsRedemptionOrder;
import com.dongqh.luckyhub.shipping.entity.ShippingAddressSnapshot;
import com.dongqh.luckyhub.shipping.entity.ShippingCallbackReceipt;
import com.dongqh.luckyhub.shipping.entity.ShippingOrder;
import com.dongqh.luckyhub.shipping.entity.ShippingTrackingEvent;
import com.dongqh.luckyhub.shipping.entity.UserShippingAddress;
import com.dongqh.luckyhub.shipping.enums.AddressStatus;
import com.dongqh.luckyhub.shipping.enums.ShippingErrorCode;
import com.dongqh.luckyhub.shipping.enums.ShippingSourceType;
import com.dongqh.luckyhub.shipping.enums.ShippingStatus;
import com.dongqh.luckyhub.shipping.enums.TrackingEventType;
import com.dongqh.luckyhub.shipping.mapper.ShippingAddressSnapshotMapper;
import com.dongqh.luckyhub.shipping.mapper.ShippingCallbackReceiptMapper;
import com.dongqh.luckyhub.shipping.mapper.ShippingOrderMapper;
import com.dongqh.luckyhub.shipping.mapper.ShippingTrackingEventMapper;
import com.dongqh.luckyhub.shipping.mapper.UserShippingAddressMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ShippingDomainContractTests {

    @Test
    void exposesExactShippingEnumsAndExtendedBenefitStates() {
        assertThat(ShippingSourceType.values()).containsExactly(
                ShippingSourceType.LOTTERY_BENEFIT,
                ShippingSourceType.CASH_ORDER,
                ShippingSourceType.POINTS_REDEMPTION);
        assertThat(ShippingStatus.values()).containsExactly(
                ShippingStatus.READY, ShippingStatus.FULFILLING, ShippingStatus.SHIPPED,
                ShippingStatus.IN_TRANSIT, ShippingStatus.DELIVERED, ShippingStatus.FAILED,
                ShippingStatus.TERMINATED);
        assertThat(TrackingEventType.values()).containsExactly(
                TrackingEventType.PICKED_UP, TrackingEventType.IN_TRANSIT,
                TrackingEventType.OUT_FOR_DELIVERY, TrackingEventType.DELIVERED);
        assertThat(AddressStatus.values()).containsExactly(AddressStatus.ACTIVE, AddressStatus.DELETED);
        assertThat(BenefitStatus.values()).containsExactly(
                BenefitStatus.PENDING, BenefitStatus.AVAILABLE, BenefitStatus.CLAIM_PENDING,
                BenefitStatus.GRANT_FAILED, BenefitStatus.CLAIMED, BenefitStatus.FULFILLING,
                BenefitStatus.SHIPPED, BenefitStatus.DELIVERED, BenefitStatus.CLAIM_EXPIRED,
                BenefitStatus.FULFILLMENT_FAILED, BenefitStatus.FULFILLMENT_TERMINATED);
    }

    @Test
    void exposesSafeBoundedShippingErrors() {
        assertThat(ShippingErrorCode.class.getInterfaces()).contains(ErrorCode.class);
        assertThat(Arrays.stream(ShippingErrorCode.values()).map(ErrorCode::code))
                .containsExactly(53001, 53002, 53003, 53004, 53005, 53006,
                        53007, 53008, 53009, 53010, 53011, 53012);
        assertThat(Arrays.stream(ShippingErrorCode.values()).map(ErrorCode::message))
                .containsExactly(
                        "收货地址不存在", "无权访问该收货地址", "收货地址参数不合法", "收货地址状态冲突",
                        "发货单不存在", "发货请求幂等参数冲突", "发货单状态冲突", "当前权益不可领取",
                        "实物权益已超过领取期限", "物流回调验签失败", "物流回调已处理", "物流请求参数不合法");
        assertThat(Arrays.stream(ShippingErrorCode.values()).map(ErrorCode::httpStatus))
                .containsExactly(
                        HttpStatus.NOT_FOUND, HttpStatus.FORBIDDEN, HttpStatus.BAD_REQUEST, HttpStatus.CONFLICT,
                        HttpStatus.NOT_FOUND, HttpStatus.CONFLICT, HttpStatus.CONFLICT, HttpStatus.CONFLICT,
                        HttpStatus.CONFLICT, HttpStatus.UNAUTHORIZED, HttpStatus.CONFLICT, HttpStatus.BAD_REQUEST);
    }

    @Test
    void mapsFivePersistentEntitiesAndLegacyCompatibilityFields() throws Exception {
        assertTable(UserShippingAddress.class, "user_shipping_address");
        assertTable(ShippingAddressSnapshot.class, "shipping_address_snapshot");
        assertTable(ShippingOrder.class, "shipping_order");
        assertTable(ShippingTrackingEvent.class, "shipping_tracking_event");
        assertTable(ShippingCallbackReceipt.class, "shipping_callback_receipt");

        assertFields(UserBenefit.class, Map.of(
                "claimDeadline", LocalDateTime.class, "claimedAt", LocalDateTime.class,
                "shippingOrderId", Long.class));
        assertFields(MallOrder.class, Map.of(
                "addressSnapshotId", Long.class, "shippingOrderId", Long.class));
        assertFields(PointsRedemptionOrder.class, Map.of(
                "addressSnapshotId", Long.class, "shippingOrderId", Long.class));
    }

    @Test
    void exposesRequiredMapperQueriesAndExplicitLocks() throws Exception {
        assertThat(UserShippingAddressMapper.class.getInterfaces()).contains(BaseMapper.class);
        assertThat(ShippingAddressSnapshotMapper.class.getInterfaces()).contains(BaseMapper.class);
        assertThat(ShippingOrderMapper.class.getInterfaces()).contains(BaseMapper.class);
        assertThat(ShippingTrackingEventMapper.class.getInterfaces()).contains(BaseMapper.class);
        assertThat(ShippingCallbackReceiptMapper.class.getInterfaces()).contains(BaseMapper.class);

        assertSelect(UserShippingAddressMapper.class.getMethod("lockById", long.class),
                "SELECT * FROM user_shipping_address WHERE id=#{id} FOR UPDATE", false);
        assertSelect(UserShippingAddressMapper.class.getMethod("lockActiveByUser", long.class),
                "SELECT * FROM user_shipping_address WHERE user_id=#{userId} AND status='ACTIVE' ORDER BY id FOR UPDATE", false);
        assertSelect(ShippingAddressSnapshotMapper.class.getMethod(
                        "selectBySource", ShippingSourceType.class, String.class),
                "SELECT * FROM shipping_address_snapshot WHERE source_type=#{type} AND source_id=#{sourceId}", true);
        assertSelect(ShippingOrderMapper.class.getMethod(
                        "lockBySource", ShippingSourceType.class, String.class),
                "SELECT * FROM shipping_order WHERE source_type=#{type} AND source_id=#{sourceId} FOR UPDATE", true);
        assertSelect(ShippingOrderMapper.class.getMethod("lockByShippingNo", String.class),
                "SELECT * FROM shipping_order WHERE shipping_no=#{shippingNo} FOR UPDATE", false);
        assertSelect(ShippingOrderMapper.class.getMethod("lockByWaybillNo", String.class),
                "SELECT * FROM shipping_order WHERE waybill_no=#{waybillNo} FOR UPDATE", false);
        assertSelect(ShippingOrderMapper.class.getMethod("selectByClaimRequestId", String.class),
                "SELECT * FROM shipping_order WHERE claim_request_id=#{claimRequestId}", false);
        assertSelect(ShippingTrackingEventMapper.class.getMethod("selectByShippingOrderId", long.class),
                "SELECT * FROM shipping_tracking_event WHERE shipping_order_id=#{shippingOrderId} ORDER BY event_time,id", false);
        assertSelect(ShippingCallbackReceiptMapper.class.getMethod("selectByCallbackId", String.class),
                "SELECT * FROM shipping_callback_receipt WHERE callback_id=#{callbackId}", false);
    }

    private void assertTable(Class<?> type, String tableName) {
        assertThat(type.getAnnotation(TableName.class)).isNotNull();
        assertThat(type.getAnnotation(TableName.class).value()).isEqualTo(tableName);
        assertThat(fieldNames(type)).contains("createdAt");
    }

    private void assertFields(Class<?> type, Map<String, Class<?>> fields) throws Exception {
        for (Map.Entry<String, Class<?>> entry : fields.entrySet()) {
            Field field = type.getDeclaredField(entry.getKey());
            assertThat(field.getType()).isEqualTo(entry.getValue());
        }
    }

    private List<String> fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).map(Field::getName).toList();
    }

    private void assertSelect(Method method, String expectedSql, boolean expectAllParamsAnnotated) {
        Select select = method.getAnnotation(Select.class);
        assertThat(select).isNotNull();
        assertThat(String.join(" ", select.value()).replaceAll("\\s+", " ").trim()).isEqualTo(expectedSql);
        if (expectAllParamsAnnotated) {
            assertThat(Arrays.stream(method.getParameters())
                    .map(parameter -> parameter.getAnnotation(Param.class))
                    .map(Param::value)
                    .collect(Collectors.toList())).doesNotContainNull();
        }
    }
}
