package com.eclakehouse.order.client;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.eclakehouse.common.client.TokenRelayInterceptor;
import com.eclakehouse.order.client.Clients.PaymentResult;
import com.eclakehouse.order.client.Clients.Split;

@Component
public class PaymentClient {

    private final RestClient http;

    public PaymentClient(RestClient.Builder builder, TokenRelayInterceptor relay,
            @Value("${ec.services.payment-url}") String baseUrl) {
        this.http = builder.baseUrl(baseUrl).requestInterceptor(relay).build();
    }

    public PaymentResult authorize(UUID orderId, BigDecimal amount, String method, int installments,
            List<Split> splits) {
        return http.post().uri("/internal/payments").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("orderId", orderId, "amount", amount, "method", method,
                        "installments", installments, "splits", splits))
                .retrieve().body(PaymentResult.class);
    }

    public void refund(UUID orderId) {
        http.post().uri("/internal/payments/{orderId}/refund", orderId).retrieve().toBodilessEntity();
    }
}
