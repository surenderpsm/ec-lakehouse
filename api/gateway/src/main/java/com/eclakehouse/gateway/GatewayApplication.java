package com.eclakehouse.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Routes are configured in application.yml. Tokens are not checked here: every service validates
 * them itself. {@code /internal/**} paths are deliberately not routed.
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
