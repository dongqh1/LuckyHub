package com.dongqh.luckyhub.shipping.controller;

import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.shipping.dto.LogisticsCallbackCommand;
import com.dongqh.luckyhub.shipping.service.LogisticsCallbackService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shipping/callbacks")
public class LogisticsCallbackController {
    private final LogisticsCallbackService service;

    public LogisticsCallbackController(LogisticsCallbackService service) {
        this.service = service;
    }

    @PostMapping("/logistics")
    public ApiResponse<Void> callback(@Valid @RequestBody LogisticsCallbackCommand command) {
        service.handle(command);
        return ApiResponse.success();
    }
}
