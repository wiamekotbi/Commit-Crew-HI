package com.commitcrew.thebakeway.domain.menu;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuItemTest {

    @Test
    void shouldCreateMenuItemSuccessfully() {
        // Act
        MenuItem item = new MenuItem("bakery-123", "Croissant", "Fresh butter croissant", 3.50, MenuItemStatus.available);

        // Assert
        assertEquals("bakery-123", item.getBakeryId());
        assertEquals("Croissant", item.getName());
        assertEquals("Fresh butter croissant", item.getDescription());
        assertEquals(3.50, item.getPrice());
        assertEquals(MenuItemStatus.available, item.getStatus());
        assertFalse(item.isDiscontinued()); // Defaults to false
    }

    @Test
    void shouldThrowExceptionWhenNameIsNullOrEmpty() {
        // Null name check
        assertThrows(IllegalArgumentException.class, () -> {
            new MenuItem("bakery-123", null, "Desc", 3.50, MenuItemStatus.available);
        });

        // Blank/Empty name check
        assertThrows(IllegalArgumentException.class, () -> {
            new MenuItem("bakery-123", "   ", "Desc", 3.50, MenuItemStatus.available);
        });
    }

    @Test
    void shouldThrowExceptionWhenPriceIsNegative() {
        assertThrows(IllegalArgumentException.class, () -> {
            new MenuItem("bakery-123", "Bagel", "Desc", -0.50, MenuItemStatus.available);
        });
    }

    @Test
    void shouldThrowExceptionWhenBakeryIdIsMissing() {
        assertThrows(IllegalArgumentException.class,
                () -> new MenuItem(null, "Bagel", "Desc", 2.50, MenuItemStatus.available));
        assertThrows(IllegalArgumentException.class,
                () -> new MenuItem("   ", "Bagel", "Desc", 2.50, MenuItemStatus.available));
    }

    @Test
    void shouldUpdateEditableDetailsWithoutChangingOwnershipOrAvailability() {
        MenuItem item = new MenuItem(
                "bakery-123", "Old Name", "Old description", 2.00, MenuItemStatus.sold_out);

        item.updateDetails("New Name", "New description", 3.50);

        assertEquals("bakery-123", item.getBakeryId());
        assertEquals("New Name", item.getName());
        assertEquals("New description", item.getDescription());
        assertEquals(3.50, item.getPrice());
        assertEquals(MenuItemStatus.sold_out, item.getStatus());
    }

    @Test
    void shouldRequireExplicitStatusForAvailabilityUpdate() {
        MenuItem item = new MenuItem(
                "bakery-123", "Cookie", "Chocolate chip", 2.00,
                MenuItemStatus.available);

        assertThrows(IllegalArgumentException.class, () -> item.updateStatus(null));
        // A rejected command must leave the current domain state unchanged.
        assertEquals(MenuItemStatus.available, item.getStatus());
    }

    @Test
    void shouldUpdateDiscontinuedStatusSuccessfully() {
        MenuItem item = new MenuItem("bakery-123", "Cookie", "Choc chip", 2.00, MenuItemStatus.available);
        
        assertFalse(item.isDiscontinued());
        
        item.setDiscontinued(true);
        assertTrue(item.isDiscontinued());
    }

    @Test
    void shouldGiveNewItemsAUniqueId() {
        MenuItem first = new MenuItem("bakery-123", "Cookie", "Choc chip", 2.00, MenuItemStatus.available);
        MenuItem second = new MenuItem("bakery-123", "Cookie", "Choc chip", 2.00, MenuItemStatus.available);

        assertNotNull(first.getId());
        assertNotEquals(first.getId(), second.getId());
    }

    @Test
    void shouldKeepTheIdFromTheCityConfig() {
        MenuItem item = new MenuItem("item-croissant", "bakery-123", "Croissant", "Butter", 3.50, MenuItemStatus.available);

        assertEquals("item-croissant", item.getId());
    }

    @Test
    void shouldRejectABlankId() {
        assertThrows(IllegalArgumentException.class,
                () -> new MenuItem(" ", "bakery-123", "Croissant", "Butter", 3.50, MenuItemStatus.available));
    }
}
