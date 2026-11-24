package com.eclakehouse.common.security;

/** Role names carried in the JWT {@code roles} claim. */
public final class Roles {
    public static final String CUSTOMER = "CUSTOMER";
    public static final String VENDOR = "VENDOR";
    public static final String ADMIN = "ADMIN";

    private Roles() {
    }
}
