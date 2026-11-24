package com.eclakehouse.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;

    @Column(nullable = false)
    private int installments;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "provider_reference")
    private String providerReference;

    @Column(name = "decline_reason")
    private String declineReason;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL)
    private List<VendorPayout> payouts = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() {
    }

    public Payment(UUID orderId, UUID customerId, BigDecimal amount, PaymentMethod method, int installments) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.method = method;
        this.installments = installments;
    }

    public void authorized(String reference) {
        this.status = PaymentStatus.AUTHORIZED;
        this.providerReference = reference;
    }

    public void declined(String reason) {
        this.status = PaymentStatus.DECLINED;
        this.declineReason = reason;
    }

    public void refunded() {
        this.status = PaymentStatus.REFUNDED;
        payouts.forEach(VendorPayout::cancel);
    }

    public void addPayout(UUID sellerId, BigDecimal gross, BigDecimal commission) {
        payouts.add(new VendorPayout(this, sellerId, gross, commission));
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public int getInstallments() {
        return installments;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public String getDeclineReason() {
        return declineReason;
    }

    public List<VendorPayout> getPayouts() {
        return payouts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
