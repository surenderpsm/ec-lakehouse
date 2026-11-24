package com.eclakehouse.vendor;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class VendorDtos {

    private VendorDtos() {
    }

    public record VendorRequest(@NotBlank @Size(max = 120) String storeName,
            @Size(max = 2000) String description,
            @NotBlank @Email String contactEmail,
            @Size(max = 10) String zipCodePrefix,
            @Size(max = 120) String city,
            @Size(max = 2) String state) {
    }

    public record VendorResponse(UUID id, String storeName, String description, String contactEmail,
            String zipCodePrefix, String city, String state, VendorStatus status, Instant approvedAt,
            Instant createdAt) {
        static VendorResponse of(Vendor v) {
            return new VendorResponse(v.getId(), v.getStoreName(), v.getDescription(), v.getContactEmail(),
                    v.getZipCodePrefix(), v.getCity(), v.getState(), v.getStatus(), v.getApprovedAt(),
                    v.getCreatedAt());
        }
    }

    /** What anyone can see about an active seller. */
    public record PublicVendorResponse(UUID id, String storeName, String description, String city, String state) {
        static PublicVendorResponse of(Vendor v) {
            return new PublicVendorResponse(v.getId(), v.getStoreName(), v.getDescription(), v.getCity(),
                    v.getState());
        }
    }
}
