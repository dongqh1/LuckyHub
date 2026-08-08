package com.dongqh.luckyhub.coupon.entity;
import com.baomidou.mybatisplus.annotation.*; import com.dongqh.luckyhub.coupon.enums.CouponType; import lombok.Getter; import lombok.Setter; import java.time.LocalDateTime;
@Getter @Setter @TableName("coupon_template") public class CouponTemplate {
 @TableId(type=IdType.AUTO) private Long id; private String templateCode; private String templateName; private CouponType couponType; private Long thresholdCent; private Long discountCent; private Long applicableProductId; private LocalDateTime validFrom; private LocalDateTime validUntil; private Integer perUserLimit; private Boolean stackableWithMembership; private Integer status; @TableField(fill=FieldFill.INSERT) private LocalDateTime createdAt; @TableField(fill=FieldFill.INSERT_UPDATE) private LocalDateTime updatedAt;
}
