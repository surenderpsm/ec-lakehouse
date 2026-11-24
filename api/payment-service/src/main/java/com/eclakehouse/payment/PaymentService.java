package com.eclakehouse.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eclakehouse.common.security.CurrentUser;
import com.eclakehouse.common.web.ApiException;
import com.eclakehouse.payment.PaymentDtos.AuthorizeRequest;
import com.eclakehouse.payment.PaymentDtos.Split;
import com.eclakehouse.payment.provider.PaymentProvider;

@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final PaymentProvider provider;
    private final BigDecimal commissionRate;

    public PaymentService(PaymentRepository payments, PaymentProvider provider,
            @Value("${ec.payment.commission-rate}") BigDecimal commissionRate) {
        this.payments = payments;
        this.provider = provider;
        this.commissionRate = commissionRate;
    }

    /** Idempotent per order: calling again for the same order returns the first result. */
    @Transactional
    public Payment authorize(UUID customerId, AuthorizeRequest req) {
        var existing = payments.findByOrderId(req.orderId());
        if (existing.isPresent()) {
            return existing.get();
        }
        BigDecimal splitTotal = req.splits().stream().map(Split::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (splitTotal.compareTo(req.amount()) != 0) {
            throw ApiException.badRequest("Vendor splits must add up to the payment amount");
        }
        int installments = req.installments() == null ? 1 : req.installments();
        Payment payment = new Payment(req.orderId(), customerId, req.amount(), req.method(), installments);
        PaymentProvider.Result result = provider.authorize(req.amount(), req.method(), installments);
        if (result.approved()) {
            payment.authorized(result.reference());
            req.splits().forEach(s -> payment.addPayout(s.sellerId(), s.amount(),
                    s.amount().multiply(commissionRate).setScale(2, RoundingMode.HALF_UP)));
        } else {
            payment.declined(result.declineReason());
        }
        return payments.save(payment);
    }

    @Transactional
    public Payment refund(UUID orderId) {
        Payment payment = payments.findByOrderId(orderId).orElseThrow(() -> ApiException.notFound("Payment"));
        if (payment.getStatus() == PaymentStatus.AUTHORIZED) {
            provider.refund(payment.getProviderReference());
            payment.refunded();
        }
        return payment;
    }

    @Transactional(readOnly = true)
    public Payment forOrder(UUID orderId) {
        Payment payment = payments.findByOrderId(orderId).orElseThrow(() -> ApiException.notFound("Payment"));
        if (!CurrentUser.isAdmin() && !payment.getCustomerId().equals(CurrentUser.id())) {
            throw ApiException.notFound("Payment");
        }
        return payment;
    }

    @Transactional(readOnly = true)
    public Page<VendorPayout> payouts(UUID sellerId, Pageable pageable) {
        return payments.findPayoutsBySeller(sellerId, pageable);
    }
}
