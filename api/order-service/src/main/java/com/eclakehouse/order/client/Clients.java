package com.eclakehouse.order.client;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Shapes of the data order-service exchanges with other services. */
public final class Clients {

    private Clients() {
    }

    public record CartItem(UUID productId, UUID sellerId, int quantity) {
    }

    public record Cart(List<CartItem> items) {
    }

    public record StockItem(UUID productId, int quantity) {
    }

    public record ReservedItem(UUID productId, UUID sellerId, String name, BigDecimal unitPrice, int quantity) {
    }

    public record Split(UUID sellerId, BigDecimal amount) {
    }

    public record PaymentResult(UUID id, String status, String declineReason) {
        public boolean authorized() {
            return "AUTHORIZED".equals(status);
        }
    }
}
