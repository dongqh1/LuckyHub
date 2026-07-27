package com.dongqh.luckyhub.activity.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class ActivitySchedulingConfiguration {
}
