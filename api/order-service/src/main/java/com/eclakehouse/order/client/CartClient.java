package com.eclakehouse.order.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.eclakehouse.common.client.TokenRelayInterceptor;
import com.eclakehouse.order.client.Clients.Cart;

@Component
public class CartClient {

    private final RestClient http;

    public CartClient(RestClient.Builder builder, TokenRelayInterceptor relay,
            @Value("${ec.services.cart-url}") String baseUrl) {
        this.http = builder.baseUrl(baseUrl).requestInterceptor(relay).build();
    }

    public Cart getCart() {
        return http.get().uri("/api/v1/cart").retrieve().body(Cart.class);
    }

    public void clear() {
        http.delete().uri("/api/v1/cart").retrieve().toBodilessEntity();
    }
}
