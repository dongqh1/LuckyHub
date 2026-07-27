package com.dongqh.luckyhub.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;


@Configuration
@MapperScan(basePackages = {
        "com.dongqh.luckyhub.rbac.mapper",
        "com.dongqh.luckyhub.prize.mapper"
})
public class MybatisPlusConfig {
}
