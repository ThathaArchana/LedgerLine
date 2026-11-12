package com.snovasys.ledgerline.dto;

import com.snovasys.ledgerline.entity.UserRole;
import com.snovasys.ledgerline.entity.UserStatus;
import java.time.LocalDateTime;

public record AdminUserResponse(
        Long id, String name, String email, UserRole role, UserStatus status, LocalDateTime createdAt) {
}