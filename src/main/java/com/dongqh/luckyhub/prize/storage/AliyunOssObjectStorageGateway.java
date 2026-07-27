package com.dongqh.luckyhub.prize.storage;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.models.PutObjectRequest;
import com.aliyun.sdk.service.oss2.transport.BinaryData;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.prize.enums.PrizeErrorCode;

public final class AliyunOssObjectStorageGateway implements ObjectStorageGateway {

    private final OSSClient client;
    private final String bucket;

    public AliyunOssObjectStorageGateway(OSSClient client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public void put(String objectKey, byte[] content, String contentType) {
        PutObjectRequest request = PutObjectRequest.newBuilder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .body(BinaryData.fromBytes(content))
                .build();
        try {
            client.putObject(request);
        } catch (RuntimeException exception) {
            throw new BusinessException(PrizeErrorCode.OSS_UPLOAD_FAILED);
        }
    }

    @Override
    public void close() throws Exception {
        client.close();
    }
}
