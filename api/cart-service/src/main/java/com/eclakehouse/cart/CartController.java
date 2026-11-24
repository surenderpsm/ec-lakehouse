package com.eclakehouse.cart;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.eclakehouse.cart.CartDtos.AddItemRequest;
import com.eclakehouse.cart.CartDtos.CartResponse;
import com.eclakehouse.cart.CartDtos.UpdateItemRequest;
import com.eclakehouse.common.security.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {

    private final CartService service;

    public CartController(CartService service) {
        this.service = service;
    }

    @GetMapping
    public CartResponse get() {
        UUID me = CurrentUser.id();
        return CartResponse.of(me, service.items(me));
    }

    @PostMapping("/items")
    public CartResponse add(@Valid @RequestBody AddItemRequest req) {
        UUID me = CurrentUser.id();
        return CartResponse.of(me, service.add(me, req.productId(), req.quantity()));
    }

    @PutMapping("/items/{productId}")
    public CartResponse update(@PathVariable UUID productId, @Valid @RequestBody UpdateItemRequest req) {
        UUID me = CurrentUser.id();
        return CartResponse.of(me, service.setQuantity(me, productId, req.quantity()));
    }

    @DeleteMapping("/items/{productId}")
    public CartResponse remove(@PathVariable UUID productId) {
        UUID me = CurrentUser.id();
        List<CartItem> items = service.remove(me, productId);
        return CartResponse.of(me, items);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear() {
        service.clear(CurrentUser.id());
    }
}
