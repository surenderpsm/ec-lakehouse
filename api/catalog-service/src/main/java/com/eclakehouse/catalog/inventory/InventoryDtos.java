package com.eclakehouse.catalog.inventory;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public final class InventoryDtos {

    private InventoryDtos() {
    }

    public record StockItem(@NotNull UUID productId, @NotNull @Min(1) Integer quantity) {
    }

    public record StockRequest(@NotEmpty List<@Valid StockItem> items) {
    }

    public record ReservedItem(UUID productId, UUID sellerId, String name, BigDecimal unitPrice, int quantity) {
    }
}
