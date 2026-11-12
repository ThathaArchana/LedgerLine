package com.snovasys.ledgerline.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountResponse(
        Long id,
        String accountNumber,
        String accountHolder,
        String ownerEmail,
        BigDecimal balance,
        String currency,
        LocalDateTime createdAt) {
}