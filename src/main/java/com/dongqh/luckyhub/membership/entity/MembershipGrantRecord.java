package com.dongqh.luckyhub.membership.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Getter; import lombok.Setter; import java.time.LocalDateTime;
@Getter @Setter @TableName("membership_grant_record") public class MembershipGrantRecord { @TableId(type=IdType.AUTO) private Long id; private String businessNo; private Long userId; private Long membershipProductId; private LocalDateTime startsAt; private LocalDateTime expiresAt; @TableField(fill=FieldFill.INSERT) private LocalDateTime createdAt; }
