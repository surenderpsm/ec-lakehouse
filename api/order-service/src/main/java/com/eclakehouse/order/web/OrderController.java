package com.eclakehouse.order.web;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.eclakehouse.common.security.CurrentUser;
import com.eclakehouse.common.web.PageResponse;
import com.eclakehouse.order.domain.VendorOrderStatus;
import com.eclakehouse.order.web.OrderDtos.CheckoutRequest;
import com.eclakehouse.order.web.OrderDtos.OrderResponse;
import com.eclakehouse.order.web.OrderDtos.VendorOrderResponse;

import jakarta.validation.Valid;

@RestController
public class OrderController {

    private final CheckoutService checkout;
    private final OrderService orders;

    public OrderController(CheckoutService checkout, OrderService orders) {
        this.checkout = checkout;
        this.orders = orders;
    }

    @PostMapping("/api/v1/orders/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse checkout(@Valid @RequestBody CheckoutRequest req) {
        return checkout.checkout(CurrentUser.id(), req);
    }

    @GetMapping("/api/v1/orders")
    public PageResponse<OrderResponse> mine(Pageable pageable) {
        return orders.mine(CurrentUser.id(), pageable);
    }

    @GetMapping("/api/v1/orders/{id}")
    public OrderResponse get(@PathVariable UUID id) {
        return orders.get(id);
    }

    @PostMapping("/api/v1/orders/{id}/cancel")
    public OrderResponse cancel(@PathVariable UUID id) {
        return orders.cancel(id);
    }

    // --- vendor side: each seller sees and fulfils only their part of an order ---

    @GetMapping("/api/v1/vendor/orders")
    public PageResponse<VendorOrderResponse> vendorOrders(@RequestParam(required = false) VendorOrderStatus status,
            Pageable pageable) {
        return orders.forSeller(CurrentUser.id(), status, pageable);
    }

    @PostMapping("/api/v1/vendor/orders/{id}/ship")
    public VendorOrderResponse ship(@PathVariable UUID id) {
        return orders.ship(id);
    }

    @PostMapping("/api/v1/vendor/orders/{id}/deliver")
    public VendorOrderResponse deliver(@PathVariable UUID id) {
        return orders.deliver(id);
    }
}
