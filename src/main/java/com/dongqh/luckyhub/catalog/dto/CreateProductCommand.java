package com.dongqh.luckyhub.catalog.dto;

import com.dongqh.luckyhub.catalog.enums.ProductType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateProductCommand(
        @NotBlank @Size(max = 64) String productCode,
        @NotBlank @Size(max = 100) String productName,
        @NotNull ProductType productType,
        @Size(max = 500) String imageUrl,
        @Size(max = 1000) String description,
        @NotBlank @Size(max = 64) String skuCode,
        @NotBlank @Size(max = 100) String skuName,
        @PositiveOrZero Long cashPriceCent,
        @PositiveOrZero Long pointsPrice,
        @NotNull Boolean cashEnabled,
        @NotNull Boolean pointsEnabled
) {
    public CreateProductCommand {
        if (Boolean.TRUE.equals(cashEnabled) && cashPriceCent == null) {
            throw new IllegalArgumentException("cashPriceCent is required when cashEnabled is true");
        }
        if (Boolean.TRUE.equals(pointsEnabled) && pointsPrice == null) {
            throw new IllegalArgumentException("pointsPrice is required when pointsEnabled is true");
        }
        if (!Boolean.TRUE.equals(cashEnabled) && !Boolean.TRUE.equals(pointsEnabled)) {
            throw new IllegalArgumentException("at least one purchase mode must be enabled");
        }
    }
}
