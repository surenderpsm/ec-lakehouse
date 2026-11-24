package com.eclakehouse.vendor;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A seller on the marketplace. The id is the owning user's id, so {@code seller_id} means the
 * same thing in every service (and matches Olist's sellers table).
 */
@Entity
@Table(name = "vendors")
public class Vendor {

    @Id
    private UUID id;

    @Column(name = "store_name", nullable = false, unique = true)
    private String storeName;

    private String description;

    @Column(name = "contact_email", nullable = false)
    private String contactEmail;

    @Column(name = "zip_code_prefix")
    private String zipCodePrefix;

    private String city;

    private String state;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VendorStatus status = VendorStatus.PENDING;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Vendor() {
    }

    public Vendor(UUID userId) {
        this.id = userId;
    }

    public void updateProfile(String storeName, String description, String contactEmail, String zipCodePrefix,
            String city, String state) {
        this.storeName = storeName;
        this.description = description;
        this.contactEmail = contactEmail;
        this.zipCodePrefix = zipCodePrefix;
        this.city = city;
        this.state = state;
    }

    public void approve(Instant now) {
        status = VendorStatus.ACTIVE;
        if (approvedAt == null) {
            approvedAt = now;
        }
    }

    public void reject() {
        status = VendorStatus.REJECTED;
    }

    public void suspend() {
        status = VendorStatus.SUSPENDED;
    }

    public UUID getId() {
        return id;
    }

    public String getStoreName() {
        return storeName;
    }

    public String getDescription() {
        return description;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public String getZipCodePrefix() {
        return zipCodePrefix;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public VendorStatus getStatus() {
        return status;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
