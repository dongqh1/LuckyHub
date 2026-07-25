package com.dongqh.luckyhub.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.dongqh.luckyhub.rbac.mapper")
public class MybatisPlusConfig {
}
