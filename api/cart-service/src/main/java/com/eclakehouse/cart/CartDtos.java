package com.eclakehouse.cart;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public final class CartDtos {

    private CartDtos() {
    }

    public record AddItemRequest(@NotNull UUID productId, @NotNull @Min(1) @Max(999) Integer quantity) {
    }

    public record UpdateItemRequest(@NotNull @Min(0) @Max(999) Integer quantity) {
    }

    public record ItemResponse(UUID productId, UUID sellerId, String productName, BigDecimal unitPrice,
            int quantity, BigDecimal lineTotal) {
        static ItemResponse of(CartItem i) {
            return new ItemResponse(i.getProductId(), i.getSellerId(), i.getProductName(), i.getUnitPrice(),
                    i.getQuantity(), i.lineTotal());
        }
    }

    public record VendorGroup(UUID sellerId, List<ItemResponse> items, BigDecimal subtotal) {
    }

    /** {@code items} is the flat list; {@code vendors} groups the same items by seller. */
    public record CartResponse(UUID customerId, List<ItemResponse> items, List<VendorGroup> vendors,
            int itemCount, BigDecimal total) {

        static CartResponse of(UUID customerId, List<CartItem> cartItems) {
            List<ItemResponse> items = cartItems.stream().map(ItemResponse::of).toList();
            Map<UUID, List<ItemResponse>> bySeller = new LinkedHashMap<>();
            items.forEach(i -> bySeller.computeIfAbsent(i.sellerId(), k -> new java.util.ArrayList<>()).add(i));
            List<VendorGroup> vendors = bySeller.entrySet().stream()
                    .map(e -> new VendorGroup(e.getKey(), e.getValue(), sum(e.getValue()))).toList();
            int count = items.stream().mapToInt(ItemResponse::quantity).sum();
            return new CartResponse(customerId, items, vendors, count, sum(items));
        }

        private static BigDecimal sum(List<ItemResponse> items) {
            return items.stream().map(ItemResponse::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }
}
