package com.dongqh.luckyhub.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelCashOrderCommand(@NotBlank @Size(max = 500) String reason) {
}
