package com.dongqh.luckyhub.integration;

import com.dongqh.luckyhub.fulfillment.enums.GatewayOutcome;
import com.dongqh.luckyhub.integration.gateway.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayContractTests {

    @Test
    void constructsFourTypedRequests() {
        CouponGrantRequest coupon = new CouponGrantRequest("FUL-1", 7L, "NEW20", 1);
        PointsGrantRequest points = new PointsGrantRequest("FUL-2", 7L, 500L, "抽奖奖励");
        MembershipGrantRequest membership = new MembershipGrantRequest("FUL-3", 7L, "VIP_MONTH", 30);
        LogisticsCreateRequest logistics = new LogisticsCreateRequest(
                "FUL-4", 7L, 99L, "SKU-CUP", 2,
                "张三", "13812345678", "浙江省", "杭州市", "西湖区", "文三路1号",
                "张*", "138****5678", "浙江省杭州市西湖区***");

        assertThat(coupon.couponTemplateCode()).isEqualTo("NEW20");
        assertThat(points.points()).isEqualTo(500L);
        assertThat(membership.durationDays()).isEqualTo(30);
        assertThat(logistics.phone()).isEqualTo("13812345678");
        assertThat(logistics.phoneMasked()).isEqualTo("138****5678");
        assertThat(logistics.toString()).isEqualTo("LogisticsCreateRequest[REDACTED]");
    }

    @Test
    void rejectsInvalidLogisticsDataWithoutEchoingSensitiveValues() {
        assertThatThrownBy(() -> new LogisticsCreateRequest(
                "FUL-4", 7L, 99L, "SKU-CUP", 1,
                "张三", "bad-phone", "浙江省", "杭州市", "西湖区", "文三路1号",
                "张*", "138****5678", "浙江省杭州市西湖区***"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("bad-phone")
                .hasMessageNotContaining("张三")
                .hasMessageNotContaining("文三路1号");
        assertThatThrownBy(() -> new LogisticsCreateRequest(
                "FUL-4", 7L, 99L, "SKU-CUP", 0,
                "张三", "13812345678", "浙江省", "杭州市", "西湖区", "文三路1号",
                "张*", "138****5678", "浙江省杭州市西湖区***"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizesImmutableGatewayResults() {
        GatewayResult success = new GatewayResult(GatewayOutcome.SUCCEEDED, "  EXT-1  ", null, null);
        GatewayResult retry = new GatewayResult(GatewayOutcome.RETRYABLE_FAILURE, " ", " TIMEOUT ", " 供应方繁忙 ");

        assertThat(success.externalReference()).isEqualTo("EXT-1");
        assertThat(retry.externalReference()).isNull();
        assertThat(retry.errorCode()).isEqualTo("TIMEOUT");
        assertThat(retry.safeMessage()).isEqualTo("供应方繁忙");
        assertThatThrownBy(() -> new GatewayResult(GatewayOutcome.SUCCEEDED, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exposesExecuteAndQueryOnEveryPort() throws Exception {
        assertThat(CouponGateway.class.getMethod("execute", CouponGrantRequest.class)).isNotNull();
        assertThat(CouponGateway.class.getMethod("query", String.class)).isNotNull();
        assertThat(PointsGateway.class.getMethod("execute", PointsGrantRequest.class)).isNotNull();
        assertThat(MembershipGateway.class.getMethod("execute", MembershipGrantRequest.class)).isNotNull();
        assertThat(LogisticsGateway.class.getMethod("execute", LogisticsCreateRequest.class)).isNotNull();
    }
}
