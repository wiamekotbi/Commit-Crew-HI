package com.commitcrew.thebakeway.domain.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BakewayAccountTest {

    @Test
    void accountStoresInformation() {
        BakewayAccount account = new BakewayAccount(
                "1",
                "maria",
                "password",
                "Maria Laghmari",
                "maria@email.com",
                "Montreal",
                Role.CUSTOMER);

        assertEquals("1", account.getAccountId());
        assertEquals("maria", account.getUsername());
        assertEquals("password", account.getPassword());
        assertEquals("Maria Laghmari", account.getDisplayName());
        assertEquals("maria@email.com", account.getContactEmail());
        assertEquals("Montreal", account.getDeliveryAddress());
        assertEquals(Role.CUSTOMER, account.getRole());
    }

    @Test
    void accountCannotHaveNullRole() {
        assertThrows(IllegalArgumentException.class, () -> new BakewayAccount(
                "1",
                "maria",
                "password",
                "Maria Laghmari",
                "maria@email.com",
                "Montreal",
                null));
    }
}