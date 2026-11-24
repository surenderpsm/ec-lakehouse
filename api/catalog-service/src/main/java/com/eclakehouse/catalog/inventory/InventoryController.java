package com.eclakehouse.catalog.inventory;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.eclakehouse.catalog.inventory.InventoryDtos.ReservedItem;
import com.eclakehouse.catalog.inventory.InventoryDtos.StockRequest;

import jakarta.validation.Valid;

/**
 * Called by order-service during checkout. {@code /internal/**} is not routed by the gateway,
 * so only services inside the network can reach it.
 */
@RestController
@RequestMapping("/internal/inventory")
public class InventoryController {

    private final InventoryService inventory;

    public InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @PostMapping("/reserve")
    public List<ReservedItem> reserve(@Valid @RequestBody StockRequest req) {
        return inventory.reserve(req.items());
    }

    @PostMapping("/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@Valid @RequestBody StockRequest req) {
        inventory.release(req.items());
    }
}
