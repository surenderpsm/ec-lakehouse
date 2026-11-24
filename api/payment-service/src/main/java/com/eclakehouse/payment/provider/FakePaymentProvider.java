package com.eclakehouse.payment.provider;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.eclakehouse.payment.PaymentMethod;

/** Approves everything up to a configurable amount. No money moves. */
@Component
public class FakePaymentProvider implements PaymentProvider {

    private final BigDecimal declineAbove;

    public FakePaymentProvider(@Value("${ec.payment.fake.decline-above}") BigDecimal declineAbove) {
        this.declineAbove = declineAbove;
    }

    @Override
    public Result authorize(BigDecimal amount, PaymentMethod method, int installments) {
        if (amount.compareTo(declineAbove) > 0) {
            return new Result(false, null, "Amount exceeds the test limit of " + declineAbove);
        }
        return new Result(true, "fake_" + UUID.randomUUID(), null);
    }

    @Override
    public void refund(String reference) {
        // nothing to do
    }
}
