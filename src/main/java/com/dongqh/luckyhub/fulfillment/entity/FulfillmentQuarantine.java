package com.dongqh.luckyhub.fulfillment.entity;
import com.baomidou.mybatisplus.annotation.*; import com.dongqh.luckyhub.fulfillment.enums.FailureCategory; import lombok.Getter; import lombok.Setter; import java.time.LocalDateTime;
@Getter @Setter @TableName("fulfillment_quarantine") public class FulfillmentQuarantine {
 @TableId(type=IdType.AUTO) private Long id; private Long taskId; private String fulfillmentNo; private String reason;
 private FailureCategory errorCategory; private String errorCode; private String errorMessage; private LocalDateTime quarantinedAt;
 private LocalDateTime resolvedAt; private String resolution; private Long resolvedBy; private String resolutionNote;
 @TableField(fill=FieldFill.INSERT) private LocalDateTime createdAt; @TableField(fill=FieldFill.INSERT_UPDATE) private LocalDateTime updatedAt;
}
