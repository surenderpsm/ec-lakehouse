package com.eclakehouse.catalog.category;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    /** Lower-case, dash-separated key, e.g. "bed_bath_table" style names from Olist work too. */
    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "parent_id")
    private UUID parentId;

    protected Category() {
    }

    public Category(String name, String slug, UUID parentId) {
        this.name = name;
        this.slug = slug;
        this.parentId = parentId;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public UUID getParentId() {
        return parentId;
    }
}
