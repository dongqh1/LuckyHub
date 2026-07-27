package com.dongqh.luckyhub.prize.config;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.credentials.StaticCredentialsProvider;
import com.dongqh.luckyhub.prize.storage.AliyunOssObjectStorageGateway;
import com.dongqh.luckyhub.prize.storage.ObjectStorageGateway;
import com.dongqh.luckyhub.prize.storage.UnavailableObjectStorageGateway;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OssProperties.class)
public class OssConfiguration {

    @Bean
    public ObjectStorageGateway objectStorageGateway(OssProperties properties) {
        if (!properties.enabled() || !properties.isComplete()) {
            return new UnavailableObjectStorageGateway();
        }
        OSSClient client = OSSClient.newBuilder()
                .region(properties.region())
                .endpoint(properties.endpoint())
                .credentialsProvider(new StaticCredentialsProvider(
                        properties.accessKeyId(),
                        properties.accessKeySecret()
                ))
                .build();
        return new AliyunOssObjectStorageGateway(client, properties.bucket());
    }
}
