package com.eclakehouse.order.domain;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface VendorOrderRepository extends JpaRepository<VendorOrder, UUID> {

    @Query(value = "select vo from VendorOrder vo join fetch vo.order o where vo.sellerId = :sellerId"
            + " order by o.createdAt desc",
            countQuery = "select count(vo) from VendorOrder vo where vo.sellerId = :sellerId")
    Page<VendorOrder> findForSeller(UUID sellerId, Pageable pageable);

    @Query(value = "select vo from VendorOrder vo join fetch vo.order o where vo.sellerId = :sellerId"
            + " and vo.status = :status order by o.createdAt desc",
            countQuery = "select count(vo) from VendorOrder vo where vo.sellerId = :sellerId and vo.status = :status")
    Page<VendorOrder> findForSeller(UUID sellerId, VendorOrderStatus status, Pageable pageable);
}
