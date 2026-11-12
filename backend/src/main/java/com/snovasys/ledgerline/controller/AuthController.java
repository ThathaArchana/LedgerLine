package com.snovasys.ledgerline.controller;

import com.snovasys.ledgerline.service.AuthService;
import com.snovasys.ledgerline.service.AuthService.AuthResponse;
import com.snovasys.ledgerline.service.AuthService.SignupResponse;
import com.snovasys.ledgerline.service.PasswordResetService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse signup(@RequestBody SignupRequest request) {
        return authService.signup(request.name(), request.email(), request.password());
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ForgotPasswordResponse forgotPassword(@RequestBody ForgotPasswordRequest request) {
        String message = passwordResetService.requestPasswordReset(request == null ? null : request.email());
        return new ForgotPasswordResponse(message);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(
                request == null ? null : request.token(),
                request == null ? null : request.newPassword());
        return ResponseEntity.noContent().build();
    }

    public record SignupRequest(String name, String email, String password) { }
    public record LoginRequest(String email, String password) { }
    public record ForgotPasswordRequest(String email) { }
    public record ResetPasswordRequest(String token, String newPassword) { }
    public record ForgotPasswordResponse(String message) { }
}