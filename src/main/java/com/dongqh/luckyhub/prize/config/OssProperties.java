package com.dongqh.luckyhub.prize.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "luckyhub.oss")
public record OssProperties(
        boolean enabled,
        String region,
        String endpoint,
        String bucket,
        String accessKeyId,
        String accessKeySecret,
        String publicBaseUrl
) {

    public boolean isComplete() {
        return StringUtils.hasText(region)
                && StringUtils.hasText(endpoint)
                && StringUtils.hasText(bucket)
                && StringUtils.hasText(accessKeyId)
                && StringUtils.hasText(accessKeySecret)
                && StringUtils.hasText(publicBaseUrl);
    }

    public String normalizedPublicBaseUrl() {
        if (!StringUtils.hasText(publicBaseUrl)) {
            return "";
        }
        return publicBaseUrl.trim().replaceAll("/+$", "");
    }
}
