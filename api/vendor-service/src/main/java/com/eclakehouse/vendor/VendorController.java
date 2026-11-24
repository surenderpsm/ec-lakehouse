package com.eclakehouse.vendor;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.eclakehouse.common.security.CurrentUser;
import com.eclakehouse.common.web.PageResponse;
import com.eclakehouse.vendor.VendorDtos.PublicVendorResponse;
import com.eclakehouse.vendor.VendorDtos.VendorRequest;
import com.eclakehouse.vendor.VendorDtos.VendorResponse;

import jakarta.validation.Valid;

@RestController
public class VendorController {

    private final VendorService service;

    public VendorController(VendorService service) {
        this.service = service;
    }

    /** Any signed-in user can apply to become a seller. */
    @PostMapping("/api/v1/vendors")
    @ResponseStatus(HttpStatus.CREATED)
    public VendorResponse apply(@Valid @RequestBody VendorRequest req) {
        return VendorResponse.of(service.apply(CurrentUser.id(), req));
    }

    @GetMapping("/api/v1/vendors/me")
    public VendorResponse me() {
        return VendorResponse.of(service.get(CurrentUser.id()));
    }

    @PutMapping("/api/v1/vendors/me")
    public VendorResponse update(@Valid @RequestBody VendorRequest req) {
        return VendorResponse.of(service.updateProfile(CurrentUser.id(), req));
    }

    @GetMapping("/api/v1/vendors")
    public PageResponse<PublicVendorResponse> listActive(Pageable pageable) {
        return PageResponse.of(service.list(VendorStatus.ACTIVE, pageable), PublicVendorResponse::of);
    }

    @GetMapping("/api/v1/vendors/{id}")
    public PublicVendorResponse get(@PathVariable UUID id) {
        return PublicVendorResponse.of(service.getActive(id));
    }

    // --- admin ---

    @GetMapping("/api/v1/admin/vendors")
    public PageResponse<VendorResponse> adminList(@RequestParam(required = false) VendorStatus status,
            Pageable pageable) {
        return PageResponse.of(service.list(status, pageable), VendorResponse::of);
    }

    @PostMapping("/api/v1/admin/vendors/{id}/approve")
    public VendorResponse approve(@PathVariable UUID id) {
        return VendorResponse.of(service.approve(id));
    }

    @PostMapping("/api/v1/admin/vendors/{id}/reject")
    public VendorResponse reject(@PathVariable UUID id) {
        return VendorResponse.of(service.reject(id));
    }

    @PostMapping("/api/v1/admin/vendors/{id}/suspend")
    public VendorResponse suspend(@PathVariable UUID id) {
        return VendorResponse.of(service.suspend(id));
    }
}
