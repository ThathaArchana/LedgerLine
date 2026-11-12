package com.snovasys.ledgerline.dto;

import com.snovasys.ledgerline.entity.LedgerSide;
import java.math.BigDecimal;

public record LedgerLineResponse(String ledgerAccount, LedgerSide side, BigDecimal amount) {
}