package com.commitcrew.thebakeway.application.reg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.commitcrew.thebakeway.domain.account.AccountRegistry;
import com.commitcrew.thebakeway.domain.account.BakewayAccount;
import com.commitcrew.thebakeway.domain.account.Role;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private AccountRegistry accountRegistry;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private RegistrationService registrationService;

    @Test
    void registersCustomer() {
        when(accountRegistry.existsByUsername("maria")).thenReturn(false);
        when(passwordEncoder.encode("password")).thenReturn("encoded-password");

        BakewayAccount account = registrationService.registerCustomer(
                "Maria",
                "maria",
                "password",
                "password",
                "Montreal");

        assertNotNull(account.getAccountId());
        assertEquals("Maria", account.getDisplayName());
        assertEquals("maria", account.getUsername());
        assertEquals("encoded-password", account.getPassword());
        assertEquals("Montreal", account.getDeliveryAddress());
        assertEquals(Role.CUSTOMER, account.getRole());
    }

    @Test
    void rejectsDuplicateUsername() {
        when(accountRegistry.existsByUsername("maria")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> registrationService.registerCustomer(
                "Maria",
                "maria",
                "password",
                "password",
                "Montreal"));
    }

    @Test
    void rejectsMismatchedPasswords() {
        assertThrows(IllegalArgumentException.class, () -> registrationService.registerCustomer(
                "Maria",
                "maria",
                "password",
                "different",
                "Montreal"));
    }

    @Test
    void rejectsMissingDeliveryAddress() {
        assertThrows(IllegalArgumentException.class, () -> registrationService.registerCustomer(
                "Maria",
                "maria",
                "password",
                "password",
                ""));
    }
}