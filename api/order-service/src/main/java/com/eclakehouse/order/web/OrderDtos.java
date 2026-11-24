package com.eclakehouse.order.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.eclakehouse.order.domain.Order;
import com.eclakehouse.order.domain.OrderItem;
import com.eclakehouse.order.domain.OrderStatus;
import com.eclakehouse.order.domain.ShippingAddress;
import com.eclakehouse.order.domain.VendorOrder;
import com.eclakehouse.order.domain.VendorOrderStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record AddressRequest(@NotBlank @Size(max = 200) String recipient,
            @NotBlank @Size(max = 300) String street,
            @NotBlank @Size(max = 120) String city,
            @NotBlank @Size(min = 2, max = 2) String state,
            @NotBlank @Size(max = 10) String zipCodePrefix) {
        public ShippingAddress toAddress() {
            return new ShippingAddress(recipient, street, city, state.toUpperCase(), zipCodePrefix);
        }
    }

    public record CheckoutRequest(@NotNull @Valid AddressRequest shippingAddress,
            @NotNull @Pattern(regexp = "CREDIT_CARD|DEBIT_CARD|BOLETO|VOUCHER") String paymentMethod,
            @Min(1) @Max(24) Integer installments) {
    }

    public record ItemResponse(UUID productId, String productName, BigDecimal unitPrice, int quantity,
            BigDecimal lineTotal) {
        static ItemResponse of(OrderItem i) {
            return new ItemResponse(i.getProductId(), i.getProductName(), i.getUnitPrice(), i.getQuantity(),
                    i.getLineTotal());
        }
    }

    public record VendorOrderResponse(UUID id, UUID orderId, UUID sellerId, VendorOrderStatus status,
            BigDecimal subtotal, List<ItemResponse> items, ShippingAddress shippingAddress, Instant placedAt,
            Instant shippedAt, Instant deliveredAt) {
        public static VendorOrderResponse of(VendorOrder vo) {
            Order o = vo.getOrder();
            return new VendorOrderResponse(vo.getId(), o.getId(), vo.getSellerId(), vo.getStatus(),
                    vo.getSubtotal(), vo.getItems().stream().map(ItemResponse::of).toList(),
                    o.getShippingAddress(), o.getPlacedAt(), vo.getShippedAt(), vo.getDeliveredAt());
        }
    }

    public record OrderResponse(UUID id, UUID customerId, OrderStatus status, BigDecimal totalAmount,
            String paymentMethod, int installments, UUID paymentId, ShippingAddress shippingAddress,
            List<VendorOrderResponse> vendorOrders, Instant createdAt, Instant placedAt) {
        public static OrderResponse of(Order o) {
            return new OrderResponse(o.getId(), o.getCustomerId(), o.getStatus(), o.getTotalAmount(),
                    o.getPaymentMethod(), o.getInstallments(), o.getPaymentId(), o.getShippingAddress(),
                    o.getVendorOrders().stream().map(VendorOrderResponse::of).toList(), o.getCreatedAt(),
                    o.getPlacedAt());
        }
    }
}
