package com.eclakehouse.order.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/** What the customer bought in one checkout. Split into one {@link VendorOrder} per seller. */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "payment_method", nullable = false)
    private String paymentMethod;

    @Column(nullable = false)
    private int installments;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Embedded
    private ShippingAddress shippingAddress;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    @OrderBy("sellerId")
    @BatchSize(size = 50)
    private List<VendorOrder> vendorOrders = new ArrayList<>();

    /** When payment was approved (Olist: order_approved_at). */
    @Column(name = "placed_at")
    private Instant placedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Order() {
    }

    public Order(UUID customerId, String paymentMethod, int installments, ShippingAddress shippingAddress) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.paymentMethod = paymentMethod;
        this.installments = installments;
        this.shippingAddress = shippingAddress;
    }

    public VendorOrder addVendorOrder(UUID sellerId) {
        VendorOrder vo = new VendorOrder(this, sellerId);
        vendorOrders.add(vo);
        return vo;
    }

    public void computeTotal() {
        vendorOrders.forEach(VendorOrder::computeSubtotal);
        totalAmount = vendorOrders.stream().map(VendorOrder::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void paid(UUID paymentId, Instant now) {
        this.paymentId = paymentId;
        this.placedAt = now;
        this.status = OrderStatus.PLACED;
        vendorOrders.forEach(vo -> vo.setStatus(VendorOrderStatus.PLACED));
    }

    public void paymentFailed(UUID paymentId) {
        this.paymentId = paymentId;
        this.status = OrderStatus.PAYMENT_FAILED;
        vendorOrders.forEach(vo -> vo.setStatus(VendorOrderStatus.PAYMENT_FAILED));
    }

    public boolean isCancellable() {
        return status == OrderStatus.PLACED;
    }

    public void cancel(Instant now) {
        status = OrderStatus.CANCELLED;
        vendorOrders.forEach(vo -> vo.cancel(now));
    }

    /** The overall status follows the sub-orders as vendors ship and deliver. */
    public void refreshStatus() {
        List<VendorOrder> live = vendorOrders.stream()
                .filter(vo -> vo.getStatus() != VendorOrderStatus.CANCELLED).toList();
        if (live.isEmpty()) {
            status = OrderStatus.CANCELLED;
        } else if (live.stream().allMatch(vo -> vo.getStatus() == VendorOrderStatus.DELIVERED)) {
            status = OrderStatus.DELIVERED;
        } else if (live.stream().allMatch(vo -> vo.getStatus() == VendorOrderStatus.SHIPPED
                || vo.getStatus() == VendorOrderStatus.DELIVERED)) {
            status = OrderStatus.SHIPPED;
        } else if (live.stream().anyMatch(vo -> vo.getStatus() == VendorOrderStatus.SHIPPED
                || vo.getStatus() == VendorOrderStatus.DELIVERED)) {
            status = OrderStatus.PARTIALLY_SHIPPED;
        } else {
            status = OrderStatus.PLACED;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public int getInstallments() {
        return installments;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public ShippingAddress getShippingAddress() {
        return shippingAddress;
    }

    public List<VendorOrder> getVendorOrders() {
        return vendorOrders;
    }

    public Instant getPlacedAt() {
        return placedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
