package com.dongqh.luckyhub.lottery.dto;

public record DrawCommand(String requestId, Long activityId, Integer drawCount) {
}
