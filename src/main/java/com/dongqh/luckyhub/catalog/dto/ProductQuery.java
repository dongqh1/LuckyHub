package com.dongqh.luckyhub.catalog.dto;

import com.dongqh.luckyhub.catalog.enums.ProductType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductQuery {
    @Min(1)
    private long page = 1;

    @Min(1)
    @Max(100)
    private long size = 20;

    @Size(max = 100)
    private String name;

    private ProductType type;

    @Min(0)
    @Max(1)
    private Integer status;
}
