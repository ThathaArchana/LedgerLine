package com.snovasys.ledgerline;

import com.snovasys.ledgerline.entity.User;
import com.snovasys.ledgerline.entity.UserRole;
import com.snovasys.ledgerline.entity.UserStatus;
import com.snovasys.ledgerline.repository.UserRepository;
import com.snovasys.ledgerline.service.AdminBootstrap;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {
    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationArguments applicationArguments;

    @Test
    void promotingExistingCustomerUsesConfiguredAdminPassword() {
        String email = "customer@example.com";
        String configuredPassword = "configured-admin-password";
        User customer = new User("Customer", email, "old-password");
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(customer));
        when(passwordEncoder.encode(configuredPassword)).thenReturn("encoded-admin-password");

        AdminBootstrap bootstrap = new AdminBootstrap(
                userRepository, passwordEncoder, email, configuredPassword);
        bootstrap.run(applicationArguments);

        assertEquals(UserRole.ADMIN, customer.getRole());
        assertEquals(UserStatus.ACTIVE, customer.getStatus());
        assertEquals("encoded-admin-password", customer.getPasswordHash());
        verify(userRepository).save(customer);
    }
}
