package com.dongqh.luckyhub.coupon.entity;
import com.baomidou.mybatisplus.annotation.*; import com.dongqh.luckyhub.coupon.enums.UserCouponStatus; import lombok.Getter; import lombok.Setter; import java.time.LocalDateTime;
@Getter @Setter @TableName("user_coupon") public class UserCoupon {
 @TableId(type=IdType.AUTO) private Long id; private String couponNo; private Long templateId; private Long userId; private UserCouponStatus status; private LocalDateTime validFrom; private LocalDateTime validUntil; private String lockedOrderNo; private String usedOrderNo; private Integer version; @TableField(fill=FieldFill.INSERT) private LocalDateTime createdAt; @TableField(fill=FieldFill.INSERT_UPDATE) private LocalDateTime updatedAt;
}
