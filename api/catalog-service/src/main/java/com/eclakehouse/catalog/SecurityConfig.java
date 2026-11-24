package com.eclakehouse.catalog;

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
                .requestMatchers(HttpMethod.GET, "/api/v1/products/**", "/api/v1/categories/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/categories").hasRole(Roles.ADMIN)
                .requestMatchers(HttpMethod.POST, "/api/v1/products").hasRole(Roles.VENDOR)
                .requestMatchers("/api/v1/vendor/**").hasRole(Roles.VENDOR));
    }
}
