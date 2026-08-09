package com.dongqh.luckyhub.shipping.support;

public final class AddressMasker {

    private AddressMasker() {
    }

    public static String maskReceiver(String receiver) {
        int firstCodePointEnd = receiver.offsetByCodePoints(0, 1);
        return receiver.codePointCount(0, receiver.length()) == 1
                ? "*"
                : receiver.substring(0, firstCodePointEnd) + "*";
    }

    public static String maskPhone(String phone) {
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    public static String maskRegion(String province, String city, String district) {
        return province + city + district + "***";
    }
}
