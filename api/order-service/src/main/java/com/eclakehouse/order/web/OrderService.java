package com.eclakehouse.order.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.eclakehouse.common.security.CurrentUser;
import com.eclakehouse.common.web.ApiException;
import com.eclakehouse.common.web.PageResponse;
import com.eclakehouse.order.client.Clients.StockItem;
import com.eclakehouse.order.client.InventoryClient;
import com.eclakehouse.order.client.PaymentClient;
import com.eclakehouse.order.domain.Order;
import com.eclakehouse.order.domain.OrderRepository;
import com.eclakehouse.order.domain.VendorOrder;
import com.eclakehouse.order.domain.VendorOrderRepository;
import com.eclakehouse.order.domain.VendorOrderStatus;
import com.eclakehouse.order.web.OrderDtos.OrderResponse;
import com.eclakehouse.order.web.OrderDtos.VendorOrderResponse;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orders;
    private final VendorOrderRepository vendorOrders;
    private final InventoryClient inventory;
    private final PaymentClient payments;
    private final TransactionTemplate tx;

    public OrderService(OrderRepository orders, VendorOrderRepository vendorOrders, InventoryClient inventory,
            PaymentClient payments, TransactionTemplate tx) {
        this.orders = orders;
        this.vendorOrders = vendorOrders;
        this.inventory = inventory;
        this.payments = payments;
        this.tx = tx;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> mine(UUID customerId, Pageable pageable) {
        return PageResponse.of(orders.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable),
                OrderResponse::of);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID id) {
        return OrderResponse.of(visible(id));
    }

    /** Customers can cancel until any vendor ships. Stock goes back and the payment is refunded. */
    public OrderResponse cancel(UUID id) {
        List<StockItem> stock = tx.execute(s -> {
            Order order = visible(id);
            if (!order.isCancellable()) {
                throw ApiException.conflict("Order can no longer be cancelled (status: " + order.getStatus() + ")");
            }
            order.cancel(Instant.now());
            return order.getVendorOrders().stream().flatMap(vo -> vo.getItems().stream())
                    .map(i -> new StockItem(i.getProductId(), i.getQuantity())).toList();
        });
        try {
            payments.refund(id);
            inventory.release(stock);
        } catch (RuntimeException e) {
            log.error("Order {} cancelled but refund or stock release failed; needs a retry", id, e);
        }
        return tx.execute(s -> OrderResponse.of(orders.findById(id).orElseThrow()));
    }

    @Transactional(readOnly = true)
    public PageResponse<VendorOrderResponse> forSeller(UUID sellerId, VendorOrderStatus status, Pageable pageable) {
        var page = status == null ? vendorOrders.findForSeller(sellerId, pageable)
                : vendorOrders.findForSeller(sellerId, status, pageable);
        return PageResponse.of(page, VendorOrderResponse::of);
    }

    @Transactional
    public VendorOrderResponse ship(UUID vendorOrderId) {
        VendorOrder vo = ownedBySeller(vendorOrderId);
        vo.ship(Instant.now());
        return VendorOrderResponse.of(vo);
    }

    @Transactional
    public VendorOrderResponse deliver(UUID vendorOrderId) {
        VendorOrder vo = ownedBySeller(vendorOrderId);
        vo.deliver(Instant.now());
        return VendorOrderResponse.of(vo);
    }

    private Order visible(UUID id) {
        Order order = orders.findById(id).orElseThrow(() -> ApiException.notFound("Order"));
        if (!CurrentUser.isAdmin() && !order.getCustomerId().equals(CurrentUser.id())) {
            throw ApiException.notFound("Order");
        }
        return order;
    }

    private VendorOrder ownedBySeller(UUID id) {
        VendorOrder vo = vendorOrders.findById(id).orElseThrow(() -> ApiException.notFound("Vendor order"));
        if (!CurrentUser.isAdmin() && !vo.getSellerId().equals(CurrentUser.id())) {
            throw ApiException.notFound("Vendor order");
        }
        return vo;
    }
}
