package com.eclakehouse.catalog.inventory;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eclakehouse.catalog.inventory.InventoryDtos.ReservedItem;
import com.eclakehouse.catalog.inventory.InventoryDtos.StockItem;
import com.eclakehouse.catalog.product.Product;
import com.eclakehouse.catalog.product.ProductRepository;
import com.eclakehouse.common.web.ApiException;

@Service
public class InventoryService {

    private final ProductRepository products;

    public InventoryService(ProductRepository products) {
        this.products = products;
    }

    /**
     * Takes stock for every item or none of them, and returns the current price and seller of each
     * product so the order is priced from the catalog, not from the client.
     */
    @Transactional
    public List<ReservedItem> reserve(List<StockItem> items) {
        Map<UUID, Integer> merged = merge(items);
        // fixed lock order (by id) so two concurrent checkouts can't deadlock
        merged.forEach((productId, qty) -> {
            if (products.takeStock(productId, qty) == 0) {
                throw ApiException.conflict("Not enough stock for product " + productId);
            }
        });
        return merged.entrySet().stream().map(e -> {
            Product p = products.findById(e.getKey()).orElseThrow();
            return new ReservedItem(p.getId(), p.getSellerId(), p.getName(), p.getPrice(), e.getValue());
        }).sorted(Comparator.comparing(ReservedItem::productId)).toList();
    }

    @Transactional
    public void release(List<StockItem> items) {
        merge(items).forEach(products::returnStock);
    }

    private static Map<UUID, Integer> merge(List<StockItem> items) {
        Map<UUID, Integer> merged = new TreeMap<>();
        items.forEach(i -> merged.merge(i.productId(), i.quantity(), Integer::sum));
        return merged;
    }
}
