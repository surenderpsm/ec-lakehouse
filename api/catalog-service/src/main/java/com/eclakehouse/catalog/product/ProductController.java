package com.eclakehouse.catalog.product;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.eclakehouse.catalog.product.ProductDtos.ProductRequest;
import com.eclakehouse.catalog.product.ProductDtos.ProductResponse;
import com.eclakehouse.catalog.product.ProductDtos.StockRequest;
import com.eclakehouse.catalog.product.ProductService.SearchCriteria;
import com.eclakehouse.common.security.CurrentUser;
import com.eclakehouse.common.web.PageResponse;

import jakarta.validation.Valid;

@RestController
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/products")
    public PageResponse<ProductResponse> search(@RequestParam(required = false) String q,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID sellerId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            Pageable pageable) {
        return PageResponse.of(service.search(new SearchCriteria(q, categoryId, sellerId, minPrice, maxPrice),
                pageable), ProductResponse::of);
    }

    @GetMapping("/api/v1/products/{id}")
    public ProductResponse get(@PathVariable UUID id) {
        return ProductResponse.of(service.getActive(id));
    }

    @PostMapping("/api/v1/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody ProductRequest req) {
        return ProductResponse.of(service.create(CurrentUser.id(), req));
    }

    @PutMapping("/api/v1/products/{id}")
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductRequest req) {
        return ProductResponse.of(service.update(id, req));
    }

    @PutMapping("/api/v1/products/{id}/stock")
    public ProductResponse setStock(@PathVariable UUID id, @Valid @RequestBody StockRequest req) {
        return ProductResponse.of(service.setStock(id, req.quantity()));
    }

    /** Products are never hard-deleted (orders reference them); they are hidden instead. */
    @DeleteMapping("/api/v1/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.deactivate(id);
    }

    /** A vendor's own products, including inactive ones. */
    @GetMapping("/api/v1/vendor/products")
    public PageResponse<ProductResponse> mine(Pageable pageable) {
        return PageResponse.of(service.listForSeller(CurrentUser.id(), pageable), ProductResponse::of);
    }
}
