package com.dongqh.luckyhub.fulfillment.model;
import com.dongqh.luckyhub.fulfillment.enums.*;import java.time.LocalDateTime;
public record FulfillmentClaim(Long taskId,String fulfillmentNo,FulfillmentType fulfillmentType,Long targetUserId,String requestPayload,AttemptOperation operation,String leaseToken,LocalDateTime claimedAt,LocalDateTime leaseUntil){}
