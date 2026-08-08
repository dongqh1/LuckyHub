package com.dongqh.luckyhub.membership.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PurchaseMyMembershipCommand(
        @NotBlank @Size(max = 64) String businessNo,
        @NotNull @Positive Long membershipProductId
) {
}
