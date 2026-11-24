package com.eclakehouse.order.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import com.eclakehouse.common.web.ApiException;

/** The part of an order one seller has to fulfil. Each seller ships independently. */
@Entity
@Table(name = "vendor_orders")
public class VendorOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VendorOrderStatus status = VendorOrderStatus.PENDING_PAYMENT;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @OneToMany(mappedBy = "vendorOrder", cascade = CascadeType.ALL)
    @BatchSize(size = 50)
    private List<OrderItem> items = new ArrayList<>();

    /** Olist: order_delivered_carrier_date. */
    @Column(name = "shipped_at")
    private Instant shippedAt;

    /** Olist: order_delivered_customer_date. */
    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    protected VendorOrder() {
    }

    VendorOrder(Order order, UUID sellerId) {
        this.order = order;
        this.sellerId = sellerId;
    }

    public void addItem(UUID productId, String name, BigDecimal unitPrice, int quantity) {
        items.add(new OrderItem(this, productId, name, unitPrice, quantity));
    }

    void computeSubtotal() {
        subtotal = items.stream().map(OrderItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    void setStatus(VendorOrderStatus status) {
        this.status = status;
    }

    public void ship(Instant now) {
        if (status != VendorOrderStatus.PLACED) {
            throw ApiException.conflict("Only placed orders can be shipped (current status: " + status + ")");
        }
        status = VendorOrderStatus.SHIPPED;
        shippedAt = now;
        order.refreshStatus();
    }

    public void deliver(Instant now) {
        if (status != VendorOrderStatus.SHIPPED) {
            throw ApiException.conflict("Only shipped orders can be delivered (current status: " + status + ")");
        }
        status = VendorOrderStatus.DELIVERED;
        deliveredAt = now;
        order.refreshStatus();
    }

    void cancel(Instant now) {
        status = VendorOrderStatus.CANCELLED;
        cancelledAt = now;
    }

    public UUID getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public UUID getSellerId() {
        return sellerId;
    }

    public VendorOrderStatus getStatus() {
        return status;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public Instant getShippedAt() {
        return shippedAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }
}
