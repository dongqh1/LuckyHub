package com.dongqh.luckyhub.shipping.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateShippingAddressCommand(
        @NotBlank @Size(max = 64) String receiverName,
        @NotBlank @Pattern(regexp = "1[3-9]\\d{9}") String phone,
        @NotBlank @Size(max = 64) String province,
        @NotBlank @Size(max = 64) String city,
        @NotBlank @Size(max = 64) String district,
        @NotBlank @Size(max = 200) String detail,
        boolean defaultAddress
) {
}
