package com.dongqh.luckyhub.prize.storage;

import com.dongqh.luckyhub.prize.config.OssConfiguration;
import com.dongqh.luckyhub.prize.config.OssProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class OssConfigurationTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(OssConfiguration.class);

    @Test
    void usesUnavailableGatewayWhenOssIsDisabled() {
        runner.run(context -> assertThat(context.getBean(ObjectStorageGateway.class))
                .isInstanceOf(UnavailableObjectStorageGateway.class));
    }

    @Test
    void buildsAliyunGatewayWhenConfigurationIsComplete() {
        runner.withPropertyValues(
                        "luckyhub.oss.enabled=true",
                        "luckyhub.oss.region=cn-hangzhou",
                        "luckyhub.oss.endpoint=https://oss-cn-hangzhou.aliyuncs.com",
                        "luckyhub.oss.bucket=luckyhub-prizes",
                        "luckyhub.oss.access-key-id=test-id",
                        "luckyhub.oss.access-key-secret=test-secret",
                        "luckyhub.oss.public-base-url=https://bucket.example.com/"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ObjectStorageGateway.class))
                            .isInstanceOf(AliyunOssObjectStorageGateway.class);
                    assertThat(context.getBean(OssProperties.class).normalizedPublicBaseUrl())
                            .isEqualTo("https://bucket.example.com");
                });
    }
}
