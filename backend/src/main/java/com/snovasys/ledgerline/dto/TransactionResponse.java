package com.snovasys.ledgerline.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record TransactionResponse(
        Long id,
        Long accountId,
        String accountNumber,
        String accountHolder,
        String ownerEmail,
        String currency,
        String transactionType,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String description,
        String idempotencyKey,
        LocalDateTime createdAt,
        List<LedgerLineResponse> ledgerEntries) {
}