package com.eclakehouse.catalog.product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ProductDtos {

    private ProductDtos() {
    }

    public record ProductRequest(UUID categoryId,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 5000) String description,
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @Min(0) Integer stockQuantity,
            @Min(0) Integer weightGrams) {
    }

    public record StockRequest(@NotNull @Min(0) Integer quantity) {
    }

    public record ProductResponse(UUID id, UUID sellerId, UUID categoryId, String name, String description,
            BigDecimal price, int stockQuantity, Integer weightGrams, ProductStatus status, Instant createdAt,
            Instant updatedAt) {
        public static ProductResponse of(Product p) {
            return new ProductResponse(p.getId(), p.getSellerId(), p.getCategoryId(), p.getName(),
                    p.getDescription(), p.getPrice(), p.getStockQuantity(), p.getWeightGrams(), p.getStatus(),
                    p.getCreatedAt(), p.getUpdatedAt());
        }
    }
}
