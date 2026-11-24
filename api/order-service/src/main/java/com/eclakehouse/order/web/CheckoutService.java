package com.eclakehouse.order.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.eclakehouse.common.web.ApiException;
import com.eclakehouse.order.client.CartClient;
import com.eclakehouse.order.client.Clients.Cart;
import com.eclakehouse.order.client.Clients.PaymentResult;
import com.eclakehouse.order.client.Clients.ReservedItem;
import com.eclakehouse.order.client.Clients.Split;
import com.eclakehouse.order.client.Clients.StockItem;
import com.eclakehouse.order.client.InventoryClient;
import com.eclakehouse.order.client.PaymentClient;
import com.eclakehouse.order.domain.Order;
import com.eclakehouse.order.domain.OrderRepository;
import com.eclakehouse.order.domain.VendorOrder;
import com.eclakehouse.order.web.OrderDtos.CheckoutRequest;
import com.eclakehouse.order.web.OrderDtos.OrderResponse;

/**
 * Checkout across services: read the cart, reserve stock (which also prices the items), save the
 * order, take payment, then empty the cart. If anything after the reservation fails, the stock is
 * given back. Remote calls happen outside database transactions.
 */
@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    private final CartClient cart;
    private final InventoryClient inventory;
    private final PaymentClient payments;
    private final OrderRepository orders;
    private final TransactionTemplate tx;

    public CheckoutService(CartClient cart, InventoryClient inventory, PaymentClient payments,
            OrderRepository orders, TransactionTemplate tx) {
        this.cart = cart;
        this.inventory = inventory;
        this.payments = payments;
        this.orders = orders;
        this.tx = tx;
    }

    public OrderResponse checkout(UUID customerId, CheckoutRequest req) {
        Cart current = cart.getCart();
        if (current == null || current.items() == null || current.items().isEmpty()) {
            throw ApiException.badRequest("Your cart is empty");
        }
        List<StockItem> stock = current.items().stream().map(i -> new StockItem(i.productId(), i.quantity()))
                .toList();
        List<ReservedItem> reserved = inventory.reserve(stock);

        PaymentResult payment;
        Order order;
        try {
            int installments = req.installments() == null ? 1 : req.installments();
            order = tx.execute(s -> orders.save(newOrder(customerId, req, installments, reserved)));
            List<Split> splits = order.getVendorOrders().stream()
                    .map(vo -> new Split(vo.getSellerId(), vo.getSubtotal())).toList();
            payment = payments.authorize(order.getId(), order.getTotalAmount(), order.getPaymentMethod(),
                    installments, splits);
        } catch (RuntimeException e) {
            releaseQuietly(stock);
            throw e;
        }

        UUID orderId = order.getId();
        if (!payment.authorized()) {
            releaseQuietly(stock);
            tx.executeWithoutResult(s -> orders.findById(orderId).orElseThrow().paymentFailed(payment.id()));
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "Payment declined: " + payment.declineReason()
                    + " (order " + orderId + ")");
        }

        OrderResponse placed = tx.execute(s -> {
            Order o = orders.findById(orderId).orElseThrow();
            o.paid(payment.id(), Instant.now());
            return OrderResponse.of(o);
        });
        try {
            cart.clear();
        } catch (RuntimeException e) {
            log.warn("Order {} placed but the cart could not be cleared", orderId, e);
        }
        return placed;
    }

    private static Order newOrder(UUID customerId, CheckoutRequest req, int installments,
            List<ReservedItem> items) {
        Order order = new Order(customerId, req.paymentMethod(), installments, req.shippingAddress().toAddress());
        Map<UUID, VendorOrder> bySeller = new LinkedHashMap<>();
        for (ReservedItem item : items) {
            bySeller.computeIfAbsent(item.sellerId(), order::addVendorOrder)
                    .addItem(item.productId(), item.name(), item.unitPrice(), item.quantity());
        }
        order.computeTotal();
        if (order.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("Order total must be positive");
        }
        return order;
    }

    private void releaseQuietly(List<StockItem> stock) {
        try {
            inventory.release(stock);
        } catch (RuntimeException e) {
            log.error("Could not release reserved stock {}", stock, e);
        }
    }
}
