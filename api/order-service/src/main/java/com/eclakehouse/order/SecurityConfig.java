package com.eclakehouse.order;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import com.eclakehouse.common.security.ResourceServerSecurity;
import com.eclakehouse.common.security.Roles;

@Configuration
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return ResourceServerSecurity.build(http, auth -> auth
                .requestMatchers("/api/v1/vendor/**").hasAnyRole(Roles.VENDOR, Roles.ADMIN));
    }
}
