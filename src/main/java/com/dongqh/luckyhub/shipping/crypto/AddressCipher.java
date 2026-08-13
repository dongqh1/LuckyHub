package com.dongqh.luckyhub.shipping.crypto;

public interface AddressCipher {
    String encrypt(String plaintext);
    String decrypt(String envelope);
}
