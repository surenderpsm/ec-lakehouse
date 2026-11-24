package com.eclakehouse.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record ShippingAddress(
        @Column(name = "ship_recipient") String recipient,
        @Column(name = "ship_street") String street,
        @Column(name = "ship_city") String city,
        @Column(name = "ship_state") String state,
        @Column(name = "ship_zip_code_prefix") String zipCodePrefix) {
}
