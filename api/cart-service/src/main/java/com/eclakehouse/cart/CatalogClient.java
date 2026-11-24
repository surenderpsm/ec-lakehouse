package com.eclakehouse.cart;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CatalogClient {

    public record ProductView(UUID id, UUID sellerId, String name, BigDecimal price, int stockQuantity,
            String status) {
    }

    private final RestClient http;

    public CatalogClient(RestClient.Builder builder, @Value("${ec.services.catalog-url}") String baseUrl) {
        this.http = builder.baseUrl(baseUrl).build();
    }

    /** Public product lookup; answers 404 if the product doesn't exist or is hidden. */
    public ProductView getProduct(UUID id) {
        return http.get().uri("/api/v1/products/{id}", id).retrieve().body(ProductView.class);
    }
}
