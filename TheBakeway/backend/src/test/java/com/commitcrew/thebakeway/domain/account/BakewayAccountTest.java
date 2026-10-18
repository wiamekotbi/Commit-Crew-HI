package com.commitcrew.thebakeway.domain.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        assertNull(account.getBakeryId());
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

    @Test
    void updatesCustomerProfile() {
        BakewayAccount account = new BakewayAccount(
                "1",
                "maria",
                "password",
                "Maria",
                "",
                "Old Address",
                Role.CUSTOMER);

        account.updateProfile("Maria Laghmari", "maria@email.com", "New Address");

        assertEquals("Maria Laghmari", account.getDisplayName());
        assertEquals("maria@email.com", account.getContactEmail());
        assertEquals("New Address", account.getDeliveryAddress());
        assertEquals(Role.CUSTOMER, account.getRole());
        assertEquals("maria", account.getUsername());
    }

    @Test
    void restaurantAccountStoresItsBakeryAssociation() {
        BakewayAccount account = new BakewayAccount(
                "restaurant-1",
                "sweet-treats-staff",
                "password",
                "Sweet Treats Staff",
                "staff@sweettreats.example",
                null,
                Role.RESTAURANT,
                "bakery-1");

        assertEquals(Role.RESTAURANT, account.getRole());
        assertEquals("bakery-1", account.getBakeryId());
    }

    @Test
    void restaurantAccountRequiresBakeryAssociation() {
        // The legacy constructor cannot create an ownerless restaurant account.
        assertThrows(IllegalArgumentException.class, () -> new BakewayAccount(
                "restaurant-1",
                "staff",
                "password",
                "Bakery Staff",
                "staff@example.com",
                null,
                Role.RESTAURANT));

        // Whitespace is not a usable persistent bakery identifier.
        assertThrows(IllegalArgumentException.class, () -> new BakewayAccount(
                "restaurant-1",
                "staff",
                "password",
                "Bakery Staff",
                "staff@example.com",
                null,
                Role.RESTAURANT,
                "   "));
    }

    @Test
    void nonRestaurantAccountCannotReceiveBakeryAssociation() {
        assertThrows(IllegalArgumentException.class, () -> new BakewayAccount(
                "customer-1",
                "customer",
                "password",
                "Customer",
                "customer@example.com",
                "Montreal",
                Role.CUSTOMER,
                "bakery-1"));
    }

    @Test
    void profileUpdateDoesNotChangeRestaurantBakeryAssociation() {
        BakewayAccount account = new BakewayAccount(
                "restaurant-1",
                "staff",
                "password",
                "Bakery Staff",
                "old@example.com",
                null,
                Role.RESTAURANT,
                "bakery-1");

        account.updateProfile("Updated Staff Name", "new@example.com", "Ignored Address");

        assertEquals("bakery-1", account.getBakeryId());
        assertEquals(Role.RESTAURANT, account.getRole());
    }
}
