package com.eclakehouse.vendor;

import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.eclakehouse.common.client.TokenRelayInterceptor;

/** Calls auth-service on the admin's behalf to give or take away the VENDOR role. */
@Component
public class AuthClient {

    private final RestClient http;

    public AuthClient(RestClient.Builder builder, TokenRelayInterceptor relay,
            @Value("${ec.services.auth-url}") String baseUrl) {
        this.http = builder.baseUrl(baseUrl).requestInterceptor(relay).build();
    }

    public void grantVendorRole(UUID userId) {
        http.post().uri("/api/v1/admin/users/{id}/roles", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("role", "VENDOR"))
                .retrieve().toBodilessEntity();
    }

    public void revokeVendorRole(UUID userId) {
        http.delete().uri("/api/v1/admin/users/{id}/roles/VENDOR", userId)
                .retrieve().toBodilessEntity();
    }
}
