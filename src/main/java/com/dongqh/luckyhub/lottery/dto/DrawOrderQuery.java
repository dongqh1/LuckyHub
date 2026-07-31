package com.dongqh.luckyhub.lottery.dto;

import com.dongqh.luckyhub.lottery.enums.DrawOrderStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public class DrawOrderQuery {
    @Min(value = 1, message = "页码不能小于1") private long page = 1;
    @Min(value = 1, message = "每页数量不能小于1") @Max(value = 100, message = "每页数量不能超过100") private long size = 20;
    @Positive(message = "用户ID必须大于0") private Long userId;
    @Positive(message = "活动ID必须大于0") private Long activityId;
    private DrawOrderStatus status;
    private LocalDate drawDate;

    public long getPage() { return page; }
    public void setPage(long page) { this.page = page; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getActivityId() { return activityId; }
    public void setActivityId(Long activityId) { this.activityId = activityId; }
    public DrawOrderStatus getStatus() { return status; }
    public void setStatus(DrawOrderStatus status) { this.status = status; }
    public LocalDate getDrawDate() { return drawDate; }
    public void setDrawDate(LocalDate drawDate) { this.drawDate = drawDate; }
}
