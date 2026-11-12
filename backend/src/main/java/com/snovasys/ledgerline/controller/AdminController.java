package com.snovasys.ledgerline.controller;

import com.snovasys.ledgerline.dto.AccountResponse;
import com.snovasys.ledgerline.dto.AdminUserResponse;
import com.snovasys.ledgerline.dto.TransactionResponse;
import com.snovasys.ledgerline.entity.User;
import com.snovasys.ledgerline.entity.UserStatus;
import com.snovasys.ledgerline.repository.UserRepository;
import com.snovasys.ledgerline.service.AccountManagementService;
import com.snovasys.ledgerline.service.TransactionService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final UserRepository userRepository;
    private final AccountManagementService accountService;
    private final TransactionService transactionService;

    public AdminController(
            UserRepository userRepository,
            AccountManagementService accountService,
            TransactionService transactionService) {
        this.userRepository = userRepository;
        this.accountService = accountService;
        this.transactionService = transactionService;
    }

    @GetMapping("/users")
    public List<AdminUserResponse> users() {
        return userRepository.findAll().stream()
                .map(user -> new AdminUserResponse(
                        user.getId(), user.getName(), user.getEmail(), user.getRole(), user.getStatus(),
                        user.getCreatedAt()))
                .toList();
    }

    @PostMapping("/users/{id}/approve")
    @Transactional
    public AdminUserResponse approveUser(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
        if (user.getStatus() != UserStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending accounts can be approved.");
        }
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        return new AdminUserResponse(
                user.getId(), user.getName(), user.getEmail(), user.getRole(), user.getStatus(), user.getCreatedAt());
    }

    @GetMapping("/accounts")
    public List<AccountResponse> accounts() {
        return accountService.listAll();
    }

    @GetMapping("/transactions")
    public List<TransactionResponse> transactions() {
        return transactionService.adminHistory();
    }
}