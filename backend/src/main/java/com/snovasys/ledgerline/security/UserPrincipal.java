package com.snovasys.ledgerline.security;

import com.snovasys.ledgerline.entity.UserRole;

public record UserPrincipal(Long id, String email, UserRole role) {
}