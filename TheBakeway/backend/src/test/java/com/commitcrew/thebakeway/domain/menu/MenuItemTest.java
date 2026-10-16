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
    void shouldUpdateDiscontinuedStatusSuccessfully() {
        MenuItem item = new MenuItem("bakery-123", "Cookie", "Choc chip", 2.00, MenuItemStatus.available);
        
        assertFalse(item.isDiscontinued());
        
        item.setDiscontinued(true);
        assertTrue(item.isDiscontinued());
    }
}