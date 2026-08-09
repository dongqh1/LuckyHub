package com.dongqh.luckyhub.integration.gateway;

public final class LogisticsCreateRequest implements GatewayRequest {
    private final String fulfillmentNo;
    private final Long targetUserId;
    private final long shippingOrderId;
    private final String skuCode;
    private final int quantity;
    private final String receiver;
    private final String phone;
    private final String province;
    private final String city;
    private final String district;
    private final String detail;
    private final String receiverMasked;
    private final String phoneMasked;
    private final String regionMasked;

    public LogisticsCreateRequest(
            String fulfillmentNo,
            Long targetUserId,
            long shippingOrderId,
            String skuCode,
            int quantity,
            String receiver,
            String phone,
            String province,
            String city,
            String district,
            String detail,
            String receiverMasked,
            String phoneMasked,
            String regionMasked
    ) {
        this.fulfillmentNo = GatewayValidation.required(fulfillmentNo, "fulfillmentNo");
        this.targetUserId = GatewayValidation.positive(targetUserId, "targetUserId");
        this.shippingOrderId = GatewayValidation.positive(shippingOrderId, "shippingOrderId");
        this.skuCode = GatewayValidation.required(skuCode, "skuCode");
        this.quantity = GatewayValidation.positive(quantity, "quantity");
        this.receiver = sensitive(receiver, "receiver", 64);
        this.phone = mobile(phone);
        this.province = sensitive(province, "province", 100);
        this.city = sensitive(city, "city", 100);
        this.district = sensitive(district, "district", 100);
        this.detail = sensitive(detail, "detail", 500);
        this.receiverMasked = masked(receiverMasked, "receiverMasked", 64);
        this.phoneMasked = maskedPhone(phoneMasked);
        this.regionMasked = masked(regionMasked, "regionMasked", 200);
    }

    private static String sensitive(String value, String field, int max) {
        String normalized = GatewayValidation.required(value, field);
        if (normalized.length() > max) {
            throw new IllegalArgumentException(field + "长度不合法");
        }
        return normalized;
    }

    private static String mobile(String value) {
        String normalized = sensitive(value, "phone", 32);
        if (!normalized.matches("1\\d{10}")) {
            throw new IllegalArgumentException("phone格式不合法");
        }
        return normalized;
    }

    private static String masked(String value, String field, int max) {
        String normalized = sensitive(value, field, max);
        if (!normalized.contains("*")) {
            throw new IllegalArgumentException(field + "必须脱敏");
        }
        return normalized;
    }

    private static String maskedPhone(String value) {
        String normalized = sensitive(value, "phoneMasked", 32);
        if (!normalized.matches("\\d{3}\\*{4}\\d{4}")) {
            throw new IllegalArgumentException("phoneMasked必须脱敏");
        }
        return normalized;
    }

    @Override public String fulfillmentNo() { return fulfillmentNo; }
    @Override public Long targetUserId() { return targetUserId; }
    public long shippingOrderId() { return shippingOrderId; }
    public String skuCode() { return skuCode; }
    public int quantity() { return quantity; }
    public String receiver() { return receiver; }
    public String phone() { return phone; }
    public String province() { return province; }
    public String city() { return city; }
    public String district() { return district; }
    public String detail() { return detail; }
    public String receiverMasked() { return receiverMasked; }
    public String phoneMasked() { return phoneMasked; }
    public String regionMasked() { return regionMasked; }

    @Override
    public String toString() {
        return "LogisticsCreateRequest[REDACTED]";
    }
}
