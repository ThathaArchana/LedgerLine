
package com.snovasys.ledgerline.service;

import com.snovasys.ledgerline.dto.LedgerLineResponse;
import com.snovasys.ledgerline.dto.TransactionResponse;
import com.snovasys.ledgerline.entity.Account;
import com.snovasys.ledgerline.entity.LedgerEntry;
import com.snovasys.ledgerline.entity.LedgerSide;
import com.snovasys.ledgerline.entity.Transaction;
import com.snovasys.ledgerline.repository.AccountRepository;
import com.snovasys.ledgerline.repository.LedgerEntryRepository;
import com.snovasys.ledgerline.repository.TransactionRepository;
import com.snovasys.ledgerline.security.UserPrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TransactionService {
    private static final String CASH_CLEARING = "ASSET:CASH_CLEARING";

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountManagementService accountManagementService;

    public TransactionService(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            LedgerEntryRepository ledgerEntryRepository,
            AccountManagementService accountManagementService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.accountManagementService = accountManagementService;
    }

    @Transactional
    public TransactionResponse deposit(
            UserPrincipal principal, Long accountId, BigDecimal amount, String description, String idempotencyKey) {
        return move(principal, accountId, amount, description, idempotencyKey, "DEPOSIT");
    }

    @Transactional
    public TransactionResponse withdraw(
            UserPrincipal principal, Long accountId, BigDecimal amount, String description, String idempotencyKey) {
        return move(principal, accountId, amount, description, idempotencyKey, "WITHDRAW");
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> history(UserPrincipal principal, Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found."));
        accountManagementService.getForTransaction(principal, account);
        return transactionRepository.findByAccount_IdOrderByCreatedAtDesc(accountId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> adminHistory() {
        return transactionRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    private TransactionResponse move(
            UserPrincipal principal,
            Long accountId,
            BigDecimal requestedAmount,
            String description,
            String idempotencyKey,
            String type) {
        BigDecimal amount = validateAmount(requestedAmount);
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9._:-]{8,100}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid Idempotency-Key header is required.");
        }
        String normalizedDescription = description == null || description.isBlank()
                ? ("DEPOSIT".equals(type) ? "Deposit" : "Withdrawal")
                : description.trim();
        if (normalizedDescription.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Description must be at most 255 characters.");
        }

        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found."));
        accountManagementService.getForTransaction(principal, account);

        Transaction previous = transactionRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (previous != null) {
            boolean sameRequest = previous.getAccount().getId().equals(accountId)
                    && previous.getTransactionType().equals(type)
                    && previous.getAmount().compareTo(amount) == 0
                    && previous.getDescription().equals(normalizedDescription);
            if (!sameRequest) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key was already used for another request.");
            }
            return toResponse(previous);
        }

        BigDecimal currentBalance = account.getBalance();
        if ("WITHDRAW".equals(type) && currentBalance.compareTo(amount) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient funds; overdrafts are not allowed.");
        }
        BigDecimal newBalance = "DEPOSIT".equals(type)
                ? currentBalance.add(amount)
                : currentBalance.subtract(amount);
        account.setBalance(newBalance);

        Transaction transaction = new Transaction();
        transaction.setAccount(account);
        transaction.setTransactionType(type);
        transaction.setAmount(amount);
        transaction.setBalanceAfter(newBalance);
        transaction.setDescription(normalizedDescription);
        transaction.setIdempotencyKey(idempotencyKey);

        try {
            transaction = transactionRepository.saveAndFlush(transaction);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key has already been processed.");
        }

        String customerLedger = "LIABILITY:CUSTOMER_ACCOUNT:" + account.getAccountNumber();
        List<LedgerEntry> entries = "DEPOSIT".equals(type)
                ? List.of(
                        new LedgerEntry(transaction, CASH_CLEARING, LedgerSide.DEBIT, amount),
                        new LedgerEntry(transaction, customerLedger, LedgerSide.CREDIT, amount))
                : List.of(
                        new LedgerEntry(transaction, customerLedger, LedgerSide.DEBIT, amount),
                        new LedgerEntry(transaction, CASH_CLEARING, LedgerSide.CREDIT, amount));
        ledgerEntryRepository.saveAll(entries);
        return toResponse(transaction);
    }

    private BigDecimal validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be greater than zero.");
        }
        try {
            BigDecimal normalized = amount.setScale(2, RoundingMode.UNNECESSARY);
            if (normalized.precision() - normalized.scale() > 17) {
                throw new ArithmeticException("Amount is too large.");
            }
            return normalized;
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must fit the account's 2-decimal currency precision.");
        }
    }

    private TransactionResponse toResponse(Transaction transaction) {
        List<LedgerLineResponse> ledgerLines = ledgerEntryRepository
                .findByTransaction_IdOrderById(transaction.getId()).stream()
                .map(entry -> new LedgerLineResponse(entry.getLedgerAccount(), entry.getSide(), entry.getAmount()))
                .toList();
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAccount().getId(),
                transaction.getAccount().getAccountNumber(),
                transaction.getAccount().getAccountHolder(),
                transaction.getAccount().getOwner() == null
                        ? null
                        : transaction.getAccount().getOwner().getEmail(),
                transaction.getAccount().getCurrency(),
                transaction.getTransactionType(),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                transaction.getDescription(),
                transaction.getIdempotencyKey(),
                transaction.getCreatedAt(),
                ledgerLines);
    }
}
