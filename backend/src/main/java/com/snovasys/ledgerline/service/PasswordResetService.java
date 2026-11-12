package com.snovasys.ledgerline.service;

import com.snovasys.ledgerline.entity.PasswordResetToken;
import com.snovasys.ledgerline.entity.User;
import com.snovasys.ledgerline.repository.PasswordResetTokenRepository;
import com.snovasys.ledgerline.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class PasswordResetService {
    private static final Logger logger = LoggerFactory.getLogger(PasswordResetService.class);
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(15);
    private static final String INVALID_TOKEN_MESSAGE = "This password reset link is invalid or has expired.";
    private static final String REQUEST_ACCEPTED_MESSAGE =
            "If an account exists for that email, password reset instructions will be sent.";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String senderAddress;
    private final String frontendUrl;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            JavaMailSender mailSender,
            @Value("${ledgerline.mail.from:}") String senderAddress,
            @Value("${ledgerline.frontend.url:http://localhost:5173}") String frontendUrl) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.senderAddress = senderAddress;
        this.frontendUrl = frontendUrl;
    }

    @Transactional
    public String requestPasswordReset(String email) {
        if (senderAddress.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Password reset email is not configured.");
        }
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!normalizedEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            return REQUEST_ACCEPTED_MESSAGE;
        }

        User user = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null) {
            return REQUEST_ACCEPTED_MESSAGE;
        }

        tokenRepository.deleteByUser_Id(user.getId());
        String rawToken = createToken();
        PasswordResetToken resetToken = tokenRepository.saveAndFlush(
                new PasswordResetToken(user, hashToken(rawToken), Instant.now().plus(TOKEN_LIFETIME)));

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderAddress);
        message.setTo(user.getEmail());
        message.setSubject("Reset your LedgerLine password");
        String resetPage = UriComponentsBuilder.fromUriString(frontendUrl)
                .replacePath("/")
                .replaceQuery(null)
                .fragment(null)
                .build()
                .toUriString();
        message.setText("Use this link within 15 minutes to reset your LedgerLine password:\n\n"
                + resetPage + "#resetToken=" + rawToken
                + "\n\nIf you did not request this, you can ignore this email.");
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            tokenRepository.delete(resetToken);
            logger.error("Could not deliver a password reset email for user id {}.", user.getId(), exception);
        }
        return REQUEST_ACCEPTED_MESSAGE;
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 72) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Password must be between 8 and 72 characters.");
        }
        if (rawToken == null || rawToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_TOKEN_MESSAGE);
        }

        PasswordResetToken resetToken = tokenRepository.findByTokenHashForUpdate(hashToken(rawToken))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_TOKEN_MESSAGE));
        if (!resetToken.getExpiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_TOKEN_MESSAGE);
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        tokenRepository.deleteByUser_Id(user.getId());
    }

    private String createToken() {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }
}
