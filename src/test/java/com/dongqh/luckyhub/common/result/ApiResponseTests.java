package com.dongqh.luckyhub.common.result;

import com.dongqh.luckyhub.common.enums.CommonErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTests {

    @Test
    void createsSuccessfulResponse() {
        Map<String, Long> data = Map.of("id", 1001L);

        ApiResponse<Map<String, Long>> response = ApiResponse.success(data);

        assertThat(response.code()).isZero();
        assertThat(response.message()).isEqualTo("success");
        assertThat(response.data()).isEqualTo(data);
    }

    @Test
    void createsErrorResponseWithTraceInformation() {
        ErrorResponse response = ErrorResponse.of(
                CommonErrorCode.INVALID_PARAMETER,
                "用户名不能为空",
                "request-123",
                1784000000000L
        );

        assertThat(response.code()).isEqualTo(30000);
        assertThat(response.message()).isEqualTo("用户名不能为空");
        assertThat(response.data()).isNull();
        assertThat(response.timestamp()).isEqualTo(1784000000000L);
        assertThat(response.requestId()).isEqualTo("request-123");
    }
}
