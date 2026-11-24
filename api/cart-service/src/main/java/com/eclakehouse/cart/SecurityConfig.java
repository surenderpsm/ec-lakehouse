package com.eclakehouse.cart;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import com.eclakehouse.common.security.ResourceServerSecurity;

/** Every cart endpoint needs a signed-in user; the cart belongs to the token's subject. */
@Configuration
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return ResourceServerSecurity.build(http, auth -> {
        });
    }
}
