package com.snovasys.ledgerline.service;

import com.snovasys.ledgerline.entity.User;
import com.snovasys.ledgerline.entity.UserRole;
import com.snovasys.ledgerline.entity.UserStatus;
import com.snovasys.ledgerline.repository.UserRepository;
import com.snovasys.ledgerline.security.JwtService;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginRateLimiter loginRateLimiter;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            LoginRateLimiter loginRateLimiter) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginRateLimiter = loginRateLimiter;
    }

    @Transactional
    public SignupResponse signup(String name, String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedName = name == null ? "" : name.trim();

        if (normalizedName.isBlank() || normalizedName.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid name.");
        }
        if (!normalizedEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid email address.");
        }
        if (password == null || password.length() < 8 || password.length() > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be between 8 and 72 characters.");
        }
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }

        User user = new User(
                normalizedName,
                normalizedEmail,
                passwordEncoder.encode(password),
                UserRole.CUSTOMER,
                UserStatus.PENDING);
        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }
        return new SignupResponse(
                user.getName(),
                user.getEmail(),
                user.getStatus().name(),
                "Signup request submitted. An admin must approve your account before you can sign in.");
    }

    public AuthResponse login(String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        loginRateLimiter.ensureAllowed(normalizedEmail);
        User user = userRepository.findByEmail(normalizedEmail).orElse(null);

        if (user == null || password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            loginRateLimiter.recordFailure(normalizedEmail);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect.");
        }
        loginRateLimiter.clear(normalizedEmail);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your account is awaiting admin approval.");
        }
        return createResponse(user);
    }

    private AuthResponse createResponse(User user) {
        return new AuthResponse(user.getName(), user.getEmail(), user.getRole().name(), jwtService.issue(user));
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public record AuthResponse(String name, String email, String role, String token) { }
    public record SignupResponse(String name, String email, String status, String message) { }
}