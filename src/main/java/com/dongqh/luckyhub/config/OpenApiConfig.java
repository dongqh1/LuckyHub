package com.dongqh.luckyhub.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI luckyHubOpenApi() {
        return new OpenAPI().info(new Info()
                .title("LuckyHub API")
                .version("v1")
                .description("LuckyHub 高并发营销抽奖与权益发放平台接口文档"));
    }
}
