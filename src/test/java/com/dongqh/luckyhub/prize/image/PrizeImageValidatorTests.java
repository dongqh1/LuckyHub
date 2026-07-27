package com.dongqh.luckyhub.prize.image;

import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.prize.enums.PrizeErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrizeImageValidatorTests {

    private final PrizeImageValidator validator = new PrizeImageValidator();

    @Test
    void rejectsEmptyImage() {
        assertError(new MockMultipartFile("file", new byte[0]), PrizeErrorCode.IMAGE_EMPTY);
    }

    @Test
    void rejectsImageLargerThanFiveMib() {
        assertError(
                new MockMultipartFile("file", "large.png", "image/png", new byte[5 * 1024 * 1024 + 1]),
                PrizeErrorCode.IMAGE_TOO_LARGE
        );
    }

    @Test
    void rejectsUnsupportedOrMismatchedContent() {
        assertError(
                new MockMultipartFile("file", "x.gif", "image/gif", new byte[]{'G', 'I', 'F'}),
                PrizeErrorCode.IMAGE_TYPE_UNSUPPORTED
        );
        assertError(
                new MockMultipartFile("file", "x.jpg", "image/jpeg",
                        new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}),
                PrizeErrorCode.IMAGE_TYPE_UNSUPPORTED
        );
    }

    @Test
    void recognizesJpegPngAndWebpMagicBytes() {
        ValidatedImage jpeg = validator.validate(new MockMultipartFile(
                "file", "x.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01}
        ));
        ValidatedImage png = validator.validate(new MockMultipartFile(
                "file", "x.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}
        ));
        ValidatedImage webp = validator.validate(new MockMultipartFile(
                "file", "x.webp", "image/webp",
                new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'}
        ));

        assertThat(jpeg.extension()).isEqualTo("jpg");
        assertThat(png.extension()).isEqualTo("png");
        assertThat(webp.extension()).isEqualTo("webp");
    }

    private void assertError(MockMultipartFile file, PrizeErrorCode errorCode) {
        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
