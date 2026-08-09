package com.dongqh.luckyhub.fulfillment.entity;
import com.baomidou.mybatisplus.annotation.*; import com.dongqh.luckyhub.fulfillment.enums.*; import lombok.Getter; import lombok.Setter; import java.time.LocalDateTime;
@Getter @Setter @TableName("fulfillment_attempt") public class FulfillmentAttempt {
 @TableId(type=IdType.AUTO) private Long id; private Long taskId; private String fulfillmentNo; private Integer sequenceNo;
 private AttemptOperation operation; private GatewayOutcome outcome; private LocalDateTime startedAt; private LocalDateTime finishedAt;
 private Long durationMs; private String externalReference; private FailureCategory errorCategory; private String errorCode; private String errorMessage;
 @TableField(fill=FieldFill.INSERT) private LocalDateTime createdAt;
}
