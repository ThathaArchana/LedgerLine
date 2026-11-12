package com.snovasys.ledgerline.service;

import com.snovasys.ledgerline.entity.User;
import com.snovasys.ledgerline.entity.UserRole;
import com.snovasys.ledgerline.entity.UserStatus;
import com.snovasys.ledgerline.repository.UserRepository;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger logger = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminBootstrap(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${ledgerline.admin.email:}") String email,
            @Value("${ledgerline.admin.password:}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() && (password == null || password.isBlank())) {
            logger.warn("Admin bootstrap is not configured; no administrator account was initialized.");
            return;
        }
        if (email.isBlank() || password == null || password.length() < 12) {
            throw new IllegalStateException("Configure both ADMIN_EMAIL and an ADMIN_PASSWORD of at least 12 characters.");
        }

        User admin = userRepository.findByEmail(email).orElseGet(() ->
                userRepository.save(new User("LedgerLine Admin", email, passwordEncoder.encode(password), UserRole.ADMIN)));
        if (admin.getRole() != UserRole.ADMIN) {
            admin.setRole(UserRole.ADMIN);
            admin.setPasswordHash(passwordEncoder.encode(password));
        }
        admin.setStatus(UserStatus.ACTIVE);
        userRepository.save(admin);
        logger.info("Admin bootstrap completed; an administrator account is active.");
    }
}