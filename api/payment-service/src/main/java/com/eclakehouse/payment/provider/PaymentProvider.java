package com.eclakehouse.payment.provider;

import java.math.BigDecimal;

import com.eclakehouse.payment.PaymentMethod;

/** Swap the fake implementation for a real gateway (Stripe, Adyen, Mercado Pago...) later. */
public interface PaymentProvider {

    record Result(boolean approved, String reference, String declineReason) {
    }

    Result authorize(BigDecimal amount, PaymentMethod method, int installments);

    void refund(String reference);
}
