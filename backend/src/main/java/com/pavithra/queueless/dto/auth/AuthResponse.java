package com.pavithra.queueless.dto.auth;

import com.pavithra.queueless.entity.Role;

public record AuthResponse(
        String token,
        Long userId,
        String fullName,
        String email,
        Role role
) {
}
