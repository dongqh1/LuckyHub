package com.dongqh.luckyhub.lottery.dto;

import com.dongqh.luckyhub.lottery.enums.DrawResultType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public class DrawRecordQuery {
    @Min(value = 1, message = "页码不能小于1") private long page = 1;
    @Min(value = 1, message = "每页数量不能小于1") @Max(value = 100, message = "每页数量不能超过100") private long size = 20;
    @Positive(message = "用户ID必须大于0") private Long userId;
    @Positive(message = "活动ID必须大于0") private Long activityId;
    private DrawResultType resultType;
    private LocalDate startDate;
    private LocalDate endDate;

    @jakarta.validation.constraints.AssertTrue(message = "开始日期不能晚于结束日期")
    public boolean isDateRangeValid() { return startDate == null || endDate == null || !startDate.isAfter(endDate); }
    public long getPage() { return page; } public void setPage(long page) { this.page = page; }
    public long getSize() { return size; } public void setSize(long size) { this.size = size; }
    public Long getUserId() { return userId; } public void setUserId(Long userId) { this.userId = userId; }
    public Long getActivityId() { return activityId; } public void setActivityId(Long activityId) { this.activityId = activityId; }
    public DrawResultType getResultType() { return resultType; } public void setResultType(DrawResultType resultType) { this.resultType = resultType; }
    public LocalDate getStartDate() { return startDate; } public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; } public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
}
