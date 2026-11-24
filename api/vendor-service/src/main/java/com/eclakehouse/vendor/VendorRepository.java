package com.eclakehouse.vendor;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorRepository extends JpaRepository<Vendor, UUID> {

    Page<Vendor> findByStatus(VendorStatus status, Pageable pageable);

    boolean existsByStoreNameIgnoreCaseAndIdNot(String storeName, UUID id);
}
