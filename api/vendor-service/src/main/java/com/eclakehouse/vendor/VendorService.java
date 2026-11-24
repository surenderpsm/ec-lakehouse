package com.eclakehouse.vendor;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eclakehouse.common.web.ApiException;
import com.eclakehouse.vendor.VendorDtos.VendorRequest;

@Service
public class VendorService {

    private final VendorRepository vendors;
    private final AuthClient authClient;

    public VendorService(VendorRepository vendors, AuthClient authClient) {
        this.vendors = vendors;
        this.authClient = authClient;
    }

    @Transactional
    public Vendor apply(UUID userId, VendorRequest req) {
        if (vendors.existsById(userId)) {
            throw ApiException.conflict("You have already applied as a vendor");
        }
        Vendor vendor = new Vendor(userId);
        applyProfile(vendor, req);
        return vendors.save(vendor);
    }

    @Transactional
    public Vendor updateProfile(UUID userId, VendorRequest req) {
        Vendor vendor = get(userId);
        applyProfile(vendor, req);
        return vendor;
    }

    @Transactional(readOnly = true)
    public Vendor get(UUID id) {
        return vendors.findById(id).orElseThrow(() -> ApiException.notFound("Vendor"));
    }

    @Transactional(readOnly = true)
    public Vendor getActive(UUID id) {
        Vendor vendor = get(id);
        if (vendor.getStatus() != VendorStatus.ACTIVE) {
            throw ApiException.notFound("Vendor");
        }
        return vendor;
    }

    @Transactional(readOnly = true)
    public Page<Vendor> list(VendorStatus status, Pageable pageable) {
        return status == null ? vendors.findAll(pageable) : vendors.findByStatus(status, pageable);
    }

    /** Activates the vendor and gives the user the VENDOR role so they can list products. */
    @Transactional
    public Vendor approve(UUID id) {
        Vendor vendor = get(id);
        if (vendor.getStatus() == VendorStatus.ACTIVE) {
            return vendor;
        }
        authClient.grantVendorRole(id);
        vendor.approve(Instant.now());
        return vendor;
    }

    @Transactional
    public Vendor reject(UUID id) {
        Vendor vendor = get(id);
        if (vendor.getStatus() != VendorStatus.PENDING) {
            throw ApiException.conflict("Only pending applications can be rejected");
        }
        vendor.reject();
        return vendor;
    }

    @Transactional
    public Vendor suspend(UUID id) {
        Vendor vendor = get(id);
        if (vendor.getStatus() != VendorStatus.ACTIVE) {
            throw ApiException.conflict("Only active vendors can be suspended");
        }
        authClient.revokeVendorRole(id);
        vendor.suspend();
        return vendor;
    }

    private void applyProfile(Vendor vendor, VendorRequest req) {
        if (vendors.existsByStoreNameIgnoreCaseAndIdNot(req.storeName(), vendor.getId())) {
            throw ApiException.conflict("Store name is already taken");
        }
        vendor.updateProfile(req.storeName(), req.description(), req.contactEmail(), req.zipCodePrefix(),
                req.city(), req.state() == null ? null : req.state().toUpperCase());
    }
}
