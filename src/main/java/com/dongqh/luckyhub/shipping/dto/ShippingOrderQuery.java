package com.dongqh.luckyhub.shipping.dto;

import com.dongqh.luckyhub.shipping.enums.ShippingSourceType;
import com.dongqh.luckyhub.shipping.enums.ShippingStatus;

public class ShippingOrderQuery {
    private long page = 1;
    private long size = 20;
    private ShippingStatus status;
    private ShippingSourceType sourceType;
    private String sourceId;
    private Long targetUserId;
    private String waybillNo;

    public long getPage() { return page; }
    public void setPage(long page) {
        if (page < 1) throw new IllegalArgumentException("page必须大于0");
        this.page = page;
    }
    public long getSize() { return size; }
    public void setSize(long size) {
        if (size < 1 || size > 100) throw new IllegalArgumentException("size必须为1到100");
        this.size = size;
    }
    public ShippingStatus getStatus() { return status; }
    public void setStatus(ShippingStatus status) { this.status = status; }
    public ShippingSourceType getSourceType() { return sourceType; }
    public void setSourceType(ShippingSourceType sourceType) { this.sourceType = sourceType; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public Long getTargetUserId() { return targetUserId; }
    public void setTargetUserId(Long targetUserId) { this.targetUserId = targetUserId; }
    public String getWaybillNo() { return waybillNo; }
    public void setWaybillNo(String waybillNo) { this.waybillNo = waybillNo; }
}
