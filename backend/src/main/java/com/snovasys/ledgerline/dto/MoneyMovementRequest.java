package com.snovasys.ledgerline.dto;

import java.math.BigDecimal;

public record MoneyMovementRequest(Long accountId, BigDecimal amount, String description) {
}