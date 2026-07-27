package com.dongqh.luckyhub.prize;

import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.prize.dto.PrizeQuery;
import com.dongqh.luckyhub.prize.enums.PrizeErrorCode;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PrizeContractTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void exposesStablePrizeErrorCodes() {
        assertThat(PrizeErrorCode.PRIZE_NOT_FOUND.code()).isEqualTo(41001);
        assertThat(PrizeErrorCode.PRIZE_NOT_FOUND.httpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(PrizeErrorCode.IMAGE_EMPTY.code()).isEqualTo(41002);
        assertThat(PrizeErrorCode.IMAGE_TYPE_UNSUPPORTED.code()).isEqualTo(41003);
        assertThat(PrizeErrorCode.IMAGE_TOO_LARGE.code()).isEqualTo(41004);
        assertThat(PrizeErrorCode.IMAGE_TOO_LARGE.httpStatus()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(PrizeErrorCode.OSS_UPLOAD_FAILED.code()).isEqualTo(51001);
        assertThat(PrizeErrorCode.OSS_UPLOAD_FAILED.httpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(PrizeErrorCode.OSS_CONFIG_UNAVAILABLE.code()).isEqualTo(51002);
        assertThat(PrizeErrorCode.OSS_CONFIG_UNAVAILABLE.httpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void appliesAndValidatesPaginationDefaults() {
        PrizeQuery defaults = new PrizeQuery();
        assertThat(defaults.getPage()).isEqualTo(1);
        assertThat(defaults.getSize()).isEqualTo(20);
        assertThat(validator.validate(defaults)).isEmpty();

        defaults.setPage(0);
        defaults.setSize(101);
        assertThat(validator.validate(defaults))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("page", "size");
    }

    @Test
    void exposesStablePageEnvelope() {
        PageResponse<String> response = new PageResponse<>(List.of("a"), 21, 2, 20, 2);

        assertThat(response.records()).containsExactly("a");
        assertThat(response.total()).isEqualTo(21);
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.pages()).isEqualTo(2);
    }
}
