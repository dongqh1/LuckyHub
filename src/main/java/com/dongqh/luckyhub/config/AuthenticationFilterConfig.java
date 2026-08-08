package com.dongqh.luckyhub.config;

import com.dongqh.luckyhub.auth.filter.AuthenticationFilter;
import com.dongqh.luckyhub.auth.security.JwtService;
import com.dongqh.luckyhub.auth.security.SessionService;
import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthenticationFilterConfig {

    @Bean
    public FilterRegistrationBean<AuthenticationFilter>
    authenticationFilterRegistration(
            JwtService jwtService,
            SessionService sessionService,
            ObjectMapper objectMapper
    ) {
        AuthenticationFilter filter =
                new AuthenticationFilter(
                        jwtService,
                        sessionService,
                        objectMapper
                );

        FilterRegistrationBean<AuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);

        registration.addUrlPatterns(
                "/api/auth/me",
                "/api/auth/logout",
                "/api/admin/*",
                "/api/lottery/*",
                "/api/benefits/*",
                "/api/products/*"
        );

        registration.setOrder(20);

        return registration;
    }
}
