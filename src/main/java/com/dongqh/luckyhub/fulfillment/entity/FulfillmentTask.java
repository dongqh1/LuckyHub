package com.dongqh.luckyhub.fulfillment.entity;
import com.baomidou.mybatisplus.annotation.*;
import com.dongqh.luckyhub.fulfillment.enums.*;
import lombok.Getter; import lombok.Setter;
import java.time.LocalDateTime;
@Getter @Setter @TableName("fulfillment_task")
public class FulfillmentTask {
 @TableId(type=IdType.AUTO) private Long id; private String fulfillmentNo; private String sourceType; private String sourceId;
 private FulfillmentType fulfillmentType; private Long targetUserId; private String requestPayload; private String requestFingerprint;
 private FulfillmentStatus status; private Integer attemptCount; private Integer maxAttempts; private LocalDateTime nextAttemptAt;
 private String leaseToken; private LocalDateTime leaseUntil; private String externalReference;
 private FailureCategory lastErrorCategory; private String lastErrorCode; private String lastErrorMessage; private LocalDateTime completedAt;
 @Version private Integer version; @TableField(fill=FieldFill.INSERT) private LocalDateTime createdAt;
 @TableField(fill=FieldFill.INSERT_UPDATE) private LocalDateTime updatedAt;
}
