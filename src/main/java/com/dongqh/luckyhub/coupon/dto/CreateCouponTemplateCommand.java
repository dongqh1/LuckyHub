package com.dongqh.luckyhub.coupon.dto;
import com.dongqh.luckyhub.coupon.enums.CouponType; import jakarta.validation.constraints.*; import java.time.LocalDateTime;
public record CreateCouponTemplateCommand(@NotBlank @Size(max=64) String templateCode,@NotBlank @Size(max=100) String templateName,@NotNull CouponType couponType,@NotNull @Min(0) Long thresholdCent,@NotNull @Positive Long discountCent,@Positive Long applicableProductId,@NotNull LocalDateTime validFrom,@NotNull LocalDateTime validUntil,@NotNull @Min(1) @Max(100) Integer perUserLimit,@NotNull Boolean stackableWithMembership) {}
