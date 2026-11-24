package com.eclakehouse.vendor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import com.eclakehouse.common.security.ResourceServerSecurity;
import com.eclakehouse.common.security.Roles;

@Configuration
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return ResourceServerSecurity.build(http, auth -> auth
                .requestMatchers("/api/v1/admin/**").hasRole(Roles.ADMIN)
                .requestMatchers(HttpMethod.GET, "/api/v1/vendors/me").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/vendors", "/api/v1/vendors/*").permitAll());
    }
}
