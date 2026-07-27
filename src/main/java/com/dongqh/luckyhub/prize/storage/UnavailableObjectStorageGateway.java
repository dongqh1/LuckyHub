package com.dongqh.luckyhub.prize.storage;

import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.prize.enums.PrizeErrorCode;

public final class UnavailableObjectStorageGateway implements ObjectStorageGateway {

    @Override
    public void put(String objectKey, byte[] content, String contentType) {
        throw new BusinessException(PrizeErrorCode.OSS_CONFIG_UNAVAILABLE);
    }
}
