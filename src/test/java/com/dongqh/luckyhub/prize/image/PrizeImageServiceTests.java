package com.dongqh.luckyhub.prize.image;

import com.dongqh.luckyhub.prize.config.OssProperties;
import com.dongqh.luckyhub.prize.storage.ObjectStorageGateway;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PrizeImageServiceTests {

    @Test
    void uploadsValidatedImageAndReturnsPublicUrl() {
        CapturingGateway gateway = new CapturingGateway();
        OssProperties properties = new OssProperties(
                true,
                "cn-hangzhou",
                "https://oss-cn-hangzhou.aliyuncs.com",
                "luckyhub-prizes",
                "key-id",
                "key-secret",
                "https://cdn.example.com/prizes/"
        );
        PrizeImageService service = new PrizeImageService(
                new PrizeImageValidator(),
                gateway,
                properties,
                Clock.fixed(Instant.parse("2026-07-27T12:00:00Z"), ZoneOffset.UTC),
                () -> UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        );

        var result = service.upload(new MockMultipartFile(
                "file", "prize.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}
        ));

        assertThat(result.objectKey())
                .isEqualTo("prizes/2026/07/123e4567-e89b-12d3-a456-426614174000.png");
        assertThat(result.url())
                .isEqualTo("https://cdn.example.com/prizes/prizes/2026/07/123e4567-e89b-12d3-a456-426614174000.png");
        assertThat(gateway.objectKey).isEqualTo(result.objectKey());
        assertThat(gateway.contentType).isEqualTo("image/png");
    }

    private static final class CapturingGateway implements ObjectStorageGateway {
        private String objectKey;
        private String contentType;

        @Override
        public void put(String objectKey, byte[] content, String contentType) {
            this.objectKey = objectKey;
            this.contentType = contentType;
        }
    }
}
