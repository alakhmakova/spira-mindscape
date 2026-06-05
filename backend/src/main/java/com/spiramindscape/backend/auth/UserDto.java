package com.spiramindscape.backend.auth;

/**
 * Lightweight user representation returned by {@code GET /api/auth/me}.
 * Never exposes internal ids or sensitive fields.
 */
public record UserDto(Long id, String email, String name, String pictureUrl) {

    public static UserDto from(AppUser user) {
        return new UserDto(user.getId(), user.getEmail(), user.getName(), user.getPictureUrl());
    }
}
