package com.snovasys.ledgerline.service;

import com.snovasys.ledgerline.dto.AccountResponse;
import com.snovasys.ledgerline.entity.Account;
import com.snovasys.ledgerline.entity.User;
import com.snovasys.ledgerline.repository.AccountRepository;
import com.snovasys.ledgerline.repository.UserRepository;
import com.snovasys.ledgerline.security.UserPrincipal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountManagementService {
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public AccountManagementService(AccountRepository accountRepository, UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public AccountResponse create(UserPrincipal principal, String requestedCurrency) {
        User owner = userRepository.findById(principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User account no longer exists."));
        String currency = requestedCurrency == null || requestedCurrency.isBlank()
                ? "INR"
                : requestedCurrency.trim().toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Currency must be a 3-letter ISO code.");
        }

        Account account = new Account();
        account.setAccountNumber("LL" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT));
        account.setAccountHolder(owner.getName());
        account.setCurrency(currency);
        account.setOwner(owner);
        return toResponse(accountRepository.save(account));
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listFor(UserPrincipal principal) {
        return accountRepository.findByOwner_IdOrderByCreatedAtDesc(principal.id()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listAll() {
        return accountRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse getFor(UserPrincipal principal, Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found."));
        if (principal.role().name().equals("ADMIN")) {
            return toResponse(account);
        }
        if (account.getOwner() == null || !account.getOwner().getId().equals(principal.id())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found.");
        }
        return toResponse(account);
    }

    public Account getForTransaction(UserPrincipal principal, Account account) {
        if (principal.role().name().equals("ADMIN")) {
            return account;
        }
        if (account.getOwner() == null || !account.getOwner().getId().equals(principal.id())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found.");
        }
        return account;
    }

    public AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getAccountHolder(),
                account.getOwner() == null ? null : account.getOwner().getEmail(),
                account.getBalance(),
                account.getCurrency(),
                account.getCreatedAt());
    }
}