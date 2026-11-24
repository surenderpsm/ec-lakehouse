package com.eclakehouse.order.domain;

public enum OrderStatus {
    PENDING_PAYMENT, PAYMENT_FAILED, PLACED, PARTIALLY_SHIPPED, SHIPPED, DELIVERED, CANCELLED
}
