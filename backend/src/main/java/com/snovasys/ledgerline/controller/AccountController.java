
package com.snovasys.ledgerline.controller;

import com.snovasys.ledgerline.dto.AccountResponse;
import com.snovasys.ledgerline.dto.CreateAccountRequest;
import com.snovasys.ledgerline.security.UserPrincipal;
import com.snovasys.ledgerline.service.AccountManagementService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountManagementService accountService;

    public AccountController(AccountManagementService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public List<AccountResponse> getAllAccounts(@AuthenticationPrincipal UserPrincipal principal) {
        return accountService.listFor(principal);
    }

    @GetMapping("/{id}")
    public AccountResponse getAccountById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return accountService.getFor(principal, id);
    }

    @PostMapping
    public AccountResponse createAccount(
            @RequestBody(required = false) CreateAccountRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return accountService.create(principal, request == null ? null : request.currency());
    }
}
