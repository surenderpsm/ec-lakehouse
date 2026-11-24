package com.eclakehouse.payment;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.eclakehouse.common.security.CurrentUser;
import com.eclakehouse.common.web.PageResponse;
import com.eclakehouse.payment.PaymentDtos.AuthorizeRequest;
import com.eclakehouse.payment.PaymentDtos.PaymentResponse;
import com.eclakehouse.payment.PaymentDtos.PayoutResponse;

import jakarta.validation.Valid;

@RestController
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    /** Called by order-service at checkout with the customer's token. Not routed by the gateway. */
    @PostMapping("/internal/payments")
    public PaymentResponse authorize(@Valid @RequestBody AuthorizeRequest req) {
        return PaymentResponse.of(service.authorize(CurrentUser.id(), req));
    }

    @PostMapping("/internal/payments/{orderId}/refund")
    public PaymentResponse refund(@PathVariable UUID orderId) {
        return PaymentResponse.of(service.refund(orderId));
    }

    @GetMapping("/api/v1/payments/orders/{orderId}")
    public PaymentResponse forOrder(@PathVariable UUID orderId) {
        return PaymentResponse.of(service.forOrder(orderId));
    }

    @GetMapping("/api/v1/vendor/payouts")
    public PageResponse<PayoutResponse> payouts(Pageable pageable) {
        return PageResponse.of(service.payouts(CurrentUser.id(), pageable), PayoutResponse::of);
    }
}
