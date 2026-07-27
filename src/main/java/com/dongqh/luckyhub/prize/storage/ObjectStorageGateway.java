package com.dongqh.luckyhub.prize.storage;

public interface ObjectStorageGateway extends AutoCloseable {

    void put(String objectKey, byte[] content, String contentType);

    @Override
    default void close() throws Exception {
    }
}
