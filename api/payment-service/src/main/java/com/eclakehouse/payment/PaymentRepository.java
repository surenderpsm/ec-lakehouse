package com.eclakehouse.payment;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    @EntityGraph(attributePaths = "payouts")
    Optional<Payment> findByOrderId(UUID orderId);

    @Query("select p from VendorPayout p where p.sellerId = :sellerId order by p.createdAt desc")
    Page<VendorPayout> findPayoutsBySeller(UUID sellerId, Pageable pageable);
}
