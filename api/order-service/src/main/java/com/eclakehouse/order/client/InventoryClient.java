package com.eclakehouse.order.client;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.eclakehouse.common.client.TokenRelayInterceptor;
import com.eclakehouse.order.client.Clients.ReservedItem;
import com.eclakehouse.order.client.Clients.StockItem;

@Component
public class InventoryClient {

    private final RestClient http;

    public InventoryClient(RestClient.Builder builder, TokenRelayInterceptor relay,
            @Value("${ec.services.catalog-url}") String baseUrl) {
        this.http = builder.baseUrl(baseUrl).requestInterceptor(relay).build();
    }

    /** All or nothing; answers 409 if any product is short. */
    public List<ReservedItem> reserve(List<StockItem> items) {
        return http.post().uri("/internal/inventory/reserve").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("items", items)).retrieve()
                .body(new ParameterizedTypeReference<List<ReservedItem>>() {
                });
    }

    public void release(List<StockItem> items) {
        http.post().uri("/internal/inventory/release").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("items", items)).retrieve().toBodilessEntity();
    }
}
