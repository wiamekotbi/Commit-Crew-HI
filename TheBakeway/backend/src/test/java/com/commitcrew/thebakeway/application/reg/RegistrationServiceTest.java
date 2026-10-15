package com.commitcrew.thebakeway.application.reg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.commitcrew.thebakeway.domain.account.AccountRegistry;
import com.commitcrew.thebakeway.domain.account.BakewayAccount;
import com.commitcrew.thebakeway.domain.account.Role;

class RegistrationServiceTest {

    @Test
    void registersCustomer() {
        AccountRegistry registry = new AccountRegistry();
        RegistrationService service = new RegistrationService(registry);

        BakewayAccount account = service.registerCustomer(
                "Maria",
                "maria",
                "password",
                "password",
                "Montreal");

        assertNotNull(account.getAccountId());
        assertEquals("Maria", account.getDisplayName());
        assertEquals("maria", account.getUsername());
        assertEquals("Montreal", account.getDeliveryAddress());
        assertEquals(Role.CUSTOMER, account.getRole());
    }

    @Test
    void rejectsDuplicateUsername() {
        AccountRegistry registry = new AccountRegistry();
        RegistrationService service = new RegistrationService(registry);

        service.registerCustomer(
                "Maria",
                "maria",
                "password",
                "password",
                "Montreal");

        assertThrows(IllegalArgumentException.class, () -> service.registerCustomer(
                "Maria 2",
                "maria",
                "password",
                "password",
                "Laval"));
    }

    @Test
    void rejectsMismatchedPasswords() {
        AccountRegistry registry = new AccountRegistry();
        RegistrationService service = new RegistrationService(registry);

        assertThrows(IllegalArgumentException.class, () -> service.registerCustomer(
                "Maria",
                "maria",
                "password",
                "different",
                "Montreal"));
    }

    @Test
    void rejectsMissingDeliveryAddress() {
        AccountRegistry registry = new AccountRegistry();
        RegistrationService service = new RegistrationService(registry);

        assertThrows(IllegalArgumentException.class, () -> service.registerCustomer(
                "Maria",
                "maria",
                "password",
                "password",
                ""));
    }
}