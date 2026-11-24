package com.eclakehouse.catalog.product;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.eclakehouse.catalog.category.CategoryRepository;
import com.eclakehouse.catalog.product.ProductDtos.ProductRequest;
import com.eclakehouse.common.security.CurrentUser;
import com.eclakehouse.common.web.ApiException;

import jakarta.persistence.criteria.Predicate;

@Service
public class ProductService {

    public record SearchCriteria(String q, UUID categoryId, UUID sellerId, BigDecimal minPrice,
            BigDecimal maxPrice) {
    }

    private final ProductRepository products;
    private final CategoryRepository categories;

    public ProductService(ProductRepository products, CategoryRepository categories) {
        this.products = products;
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public Page<Product> search(SearchCriteria c, Pageable pageable) {
        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(root.get("status"), ProductStatus.ACTIVE));
            if (StringUtils.hasText(c.q())) {
                String like = "%" + c.q().toLowerCase() + "%";
                where.add(cb.or(cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("description")), like)));
            }
            if (c.categoryId() != null) {
                where.add(cb.equal(root.get("categoryId"), c.categoryId()));
            }
            if (c.sellerId() != null) {
                where.add(cb.equal(root.get("sellerId"), c.sellerId()));
            }
            if (c.minPrice() != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("price"), c.minPrice()));
            }
            if (c.maxPrice() != null) {
                where.add(cb.lessThanOrEqualTo(root.get("price"), c.maxPrice()));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        return products.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public Product getActive(UUID id) {
        return products.findById(id).filter(p -> p.getStatus() == ProductStatus.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("Product"));
    }

    @Transactional(readOnly = true)
    public Page<Product> listForSeller(UUID sellerId, Pageable pageable) {
        return products.findBySellerId(sellerId, pageable);
    }

    @Transactional
    public Product create(UUID sellerId, ProductRequest req) {
        Product product = new Product(sellerId);
        apply(product, req);
        product.setStockQuantity(req.stockQuantity() == null ? 0 : req.stockQuantity());
        return products.save(product);
    }

    @Transactional
    public Product update(UUID id, ProductRequest req) {
        Product product = owned(id);
        apply(product, req);
        if (req.stockQuantity() != null) {
            product.setStockQuantity(req.stockQuantity());
        }
        product.setStatus(ProductStatus.ACTIVE);
        return product;
    }

    @Transactional
    public Product setStock(UUID id, int quantity) {
        Product product = owned(id);
        product.setStockQuantity(quantity);
        return product;
    }

    @Transactional
    public void deactivate(UUID id) {
        owned(id).setStatus(ProductStatus.INACTIVE);
    }

    /** The product, if the caller is its seller or an admin. */
    private Product owned(UUID id) {
        Product product = products.findById(id).orElseThrow(() -> ApiException.notFound("Product"));
        if (!CurrentUser.isAdmin() && !product.getSellerId().equals(CurrentUser.id())) {
            throw ApiException.forbidden("You can only change your own products");
        }
        return product;
    }

    private void apply(Product product, ProductRequest req) {
        if (req.categoryId() != null && !categories.existsById(req.categoryId())) {
            throw ApiException.badRequest("Category does not exist");
        }
        product.update(req.categoryId(), req.name(), req.description(), req.price(), req.weightGrams());
    }
}
