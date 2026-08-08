package com.dongqh.luckyhub.coupon.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Getter; import lombok.Setter; import java.time.LocalDateTime;
@Getter @Setter @TableName("coupon_issue_record") public class CouponIssueRecord { @TableId(type=IdType.AUTO) private Long id; private String businessNo; private Long templateId; private Long userId; private Long userCouponId; @TableField(fill=FieldFill.INSERT) private LocalDateTime createdAt; }
