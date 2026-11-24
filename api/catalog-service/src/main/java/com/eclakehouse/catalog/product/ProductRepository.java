package com.eclakehouse.catalog.product;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    Page<Product> findBySellerId(UUID sellerId, Pageable pageable);

    /** Atomically takes stock if enough is left. Returns 1 on success, 0 if not. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Product p set p.stockQuantity = p.stockQuantity - :qty
            where p.id = :id and p.status = com.eclakehouse.catalog.product.ProductStatus.ACTIVE
              and p.stockQuantity >= :qty""")
    int takeStock(@Param("id") UUID id, @Param("qty") int qty);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Product p set p.stockQuantity = p.stockQuantity + :qty where p.id = :id")
    int returnStock(@Param("id") UUID id, @Param("qty") int qty);
}
