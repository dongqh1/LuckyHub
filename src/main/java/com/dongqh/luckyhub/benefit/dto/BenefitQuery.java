package com.dongqh.luckyhub.benefit.dto;

import com.dongqh.luckyhub.benefit.enums.BenefitStatus;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public class BenefitQuery {
    private static final long MAX_PAGE=1_000_000L;
    private static final LocalDate MYSQL_MIN_DATE=LocalDate.of(1000,1,1);
    private static final LocalDate MAX_SAFE_END_DATE=LocalDate.of(9999,12,30);
    @Min(value=1,message="页码不能小于1") @Max(value=MAX_PAGE,message="页码不能超过1000000") private long page=1;
    @Min(value=1,message="每页数量不能小于1") @Max(value=100,message="每页数量不能超过100") private long size=20;
    @Positive(message="用户ID必须大于0") private Long userId;
    private BenefitStatus status; private PrizeType prizeType; private LocalDate startDate; private LocalDate endDate;
    @AssertTrue(message="日期范围无效或超出支持范围") public boolean isDateRangeValid(){return inRange(startDate,false)&&inRange(endDate,true)&&(startDate==null||endDate==null||!startDate.isAfter(endDate));}
    private boolean inRange(LocalDate date,boolean end){if(date==null)return true;LocalDate maximum=end?MAX_SAFE_END_DATE:MAX_SAFE_END_DATE.plusDays(1);return !date.isBefore(MYSQL_MIN_DATE)&&!date.isAfter(maximum);}
    public long getPage(){return page;} public void setPage(long v){page=v;} public long getSize(){return size;} public void setSize(long v){size=v;}
    public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;} public BenefitStatus getStatus(){return status;} public void setStatus(BenefitStatus v){status=v;}
    public PrizeType getPrizeType(){return prizeType;} public void setPrizeType(PrizeType v){prizeType=v;} public LocalDate getStartDate(){return startDate;} public void setStartDate(LocalDate v){startDate=v;} public LocalDate getEndDate(){return endDate;} public void setEndDate(LocalDate v){endDate=v;}
}
