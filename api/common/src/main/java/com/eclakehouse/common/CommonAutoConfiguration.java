package com.eclakehouse.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.eclakehouse.common.client.TokenRelayInterceptor;
import com.eclakehouse.common.web.GlobalExceptionHandler;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/** Registered for every service that depends on {@code common}. */
@AutoConfiguration
@Import(GlobalExceptionHandler.class)
public class CommonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    TokenRelayInterceptor tokenRelayInterceptor() {
        return new TokenRelayInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean
    OpenAPI serviceOpenApi(@Value("${spring.application.name:service}") String name) {
        return new OpenAPI()
                .info(new Info().title(name).version("v1"))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
