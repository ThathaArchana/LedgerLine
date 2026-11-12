
package com.snovasys.ledgerline.controller;

import com.snovasys.ledgerline.dto.MoneyMovementRequest;
import com.snovasys.ledgerline.dto.TransactionResponse;
import com.snovasys.ledgerline.security.UserPrincipal;
import com.snovasys.ledgerline.service.TransactionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/deposit")
    public TransactionResponse deposit(
            @RequestBody MoneyMovementRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal) {
        return transactionService.deposit(principal, request.accountId(), request.amount(), request.description(), idempotencyKey);
    }

    @PostMapping("/withdraw")
    public TransactionResponse withdraw(
            @RequestBody MoneyMovementRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal) {
        return transactionService.withdraw(principal, request.accountId(), request.amount(), request.description(), idempotencyKey);
    }

    @GetMapping("/{accountId}")
    public List<TransactionResponse> getTransactionHistory(
            @PathVariable Long accountId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return transactionService.history(principal, accountId);
    }
}
