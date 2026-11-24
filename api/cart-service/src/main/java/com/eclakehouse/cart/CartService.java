package com.eclakehouse.cart;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eclakehouse.cart.CatalogClient.ProductView;
import com.eclakehouse.common.web.ApiException;

@Service
public class CartService {

    private final CartRepository carts;
    private final CatalogClient catalog;

    public CartService(CartRepository carts, CatalogClient catalog) {
        this.carts = carts;
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public List<CartItem> items(UUID customerId) {
        return carts.findByCustomerId(customerId).map(Cart::getItems).orElse(List.of());
    }

    /** Adds to the quantity already in the cart. */
    @Transactional
    public List<CartItem> add(UUID customerId, UUID productId, int quantity) {
        Cart cart = carts.findByCustomerId(customerId).orElseGet(() -> carts.save(new Cart(customerId)));
        int newQty = cart.find(productId).map(CartItem::getQuantity).orElse(0) + quantity;
        upsert(cart, productId, newQty);
        return cart.getItems();
    }

    /** Sets the quantity; 0 removes the item. */
    @Transactional
    public List<CartItem> setQuantity(UUID customerId, UUID productId, int quantity) {
        Cart cart = carts.findByCustomerId(customerId).orElseThrow(() -> ApiException.notFound("Cart item"));
        if (cart.find(productId).isEmpty()) {
            throw ApiException.notFound("Cart item");
        }
        if (quantity == 0) {
            cart.remove(productId);
        } else {
            upsert(cart, productId, quantity);
        }
        return cart.getItems();
    }

    @Transactional
    public List<CartItem> remove(UUID customerId, UUID productId) {
        return carts.findByCustomerId(customerId).map(cart -> {
            cart.remove(productId);
            return cart.getItems();
        }).orElse(List.of());
    }

    @Transactional
    public void clear(UUID customerId) {
        carts.findByCustomerId(customerId).ifPresent(Cart::clear);
    }

    private void upsert(Cart cart, UUID productId, int quantity) {
        ProductView product = catalog.getProduct(productId);
        if (product.stockQuantity() < quantity) {
            throw ApiException.conflict("Only " + product.stockQuantity() + " left in stock");
        }
        cart.find(productId).ifPresentOrElse(
                item -> item.refresh(product.name(), product.price(), quantity),
                () -> cart.add(productId, product.sellerId(), product.name(), product.price(), quantity));
    }
}
