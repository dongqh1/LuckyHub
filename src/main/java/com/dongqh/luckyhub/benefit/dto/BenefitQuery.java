package com.dongqh.luckyhub.benefit.dto;

import com.dongqh.luckyhub.benefit.enums.BenefitStatus;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public class BenefitQuery {
    @Min(value=1,message="页码不能小于1") private long page=1;
    @Min(value=1,message="每页数量不能小于1") @Max(value=100,message="每页数量不能超过100") private long size=20;
    @Positive(message="用户ID必须大于0") private Long userId;
    private BenefitStatus status; private PrizeType prizeType; private LocalDate startDate; private LocalDate endDate;
    @AssertTrue(message="开始日期不能晚于结束日期") public boolean isDateRangeValid(){return startDate==null||endDate==null||!startDate.isAfter(endDate);}
    public long getPage(){return page;} public void setPage(long v){page=v;} public long getSize(){return size;} public void setSize(long v){size=v;}
    public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;} public BenefitStatus getStatus(){return status;} public void setStatus(BenefitStatus v){status=v;}
    public PrizeType getPrizeType(){return prizeType;} public void setPrizeType(PrizeType v){prizeType=v;} public LocalDate getStartDate(){return startDate;} public void setStartDate(LocalDate v){startDate=v;} public LocalDate getEndDate(){return endDate;} public void setEndDate(LocalDate v){endDate=v;}
}
