package com.spiramindscape.backend.auth;

/**
 * The identity claims extracted from a verified Google ID token during native mobile
 * sign-in. {@code name} and {@code pictureUrl} may be null.
 */
public record VerifiedGoogleUser(String sub, String email, String name, String pictureUrl) {}
