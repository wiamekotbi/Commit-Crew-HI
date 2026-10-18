package com.commitcrew.thebakeway.domain.bakery;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class BakeryTest {

    @Test // Bakery Creation
    void shouldCreateBakerySuccessfully() {
       
        // Act
        Bakery bakery = new Bakery("bakery-123", "Sweet Treats", BakeryStatus.open, "123 Main St");

        // Assert
        assertEquals("bakery-123", bakery.getBakeryId());
        assertEquals("Sweet Treats", bakery.getBakeryName());
        assertEquals(BakeryStatus.open, bakery.getBakeryStatus());
        assertEquals("123 Main St", bakery.getBakeryAddress()); 

        // Additional Act and Assert for other statuses

        // Act
        Bakery bakery2 = new Bakery("bakery-124", "Savory Bites", BakeryStatus.paused, "36 rue de la Paix");

        // Assert
        assertEquals(BakeryStatus.paused, bakery2.getBakeryStatus());

        // Act
        Bakery bakery3 = new Bakery("bakery-125", "Daily Bread", BakeryStatus.closed, "134 avenue Jack");
        
        // Assert
        assertEquals(BakeryStatus.closed, bakery3.getBakeryStatus());
    }


    @Test // Bakery Name
    void shouldThrowExceptionWhenBakeryNameIsNullOrEmpty() {
        
        // Null name check
        assertThrows(IllegalArgumentException.class, () -> {
            new Bakery("bakery-123", null, BakeryStatus.open, "123 Main St");
        });

        // Blank/Empty name check
        assertThrows(IllegalArgumentException.class, () -> {
            new Bakery("bakery-123", "   ", BakeryStatus.open, "123 Main St");
        });

        // Blank name without spaces check
        assertThrows(IllegalArgumentException.class, () -> {
            new Bakery("bakery-123", "", BakeryStatus.open, "123 Main St");
        });
    }

    @Test // Bakery Address
    void shouldThrowExceptionWhenBakeryAddressIsNullOrEmpty() {
        
        // Null address check
        assertThrows(IllegalArgumentException.class, () -> {
            new Bakery("bakery-123", "Sweet Treats", BakeryStatus.open, null);
        });

        // Blank/Empty address check
        assertThrows(IllegalArgumentException.class, () -> {
            new Bakery("bakery-123", "Sweet Treats", BakeryStatus.open, "   ");
        });

        // Blank address without spaces check
        assertThrows(IllegalArgumentException.class, () -> {
            new Bakery("bakery-123", "Sweet Treats", BakeryStatus.open, "");
        });
    }

    @Test // Bakery Status
    void shouldThrowExceptionWhenBakeryStatusIsNull() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Bakery("bakery-123", "Sweet Treats", null, "123 Main St");
        });
    }

    @Test // Bakery ID
    void shouldThrowExceptionWhenBakeryIdIsNullOrEmpty() {
        // Null ID check
        assertThrows(IllegalArgumentException.class, () -> {
            new Bakery(null, "Sweet Treats", BakeryStatus.open, "123 Main St");
        });

        // Blank/Empty ID check
        assertThrows(IllegalArgumentException.class, () -> {
            new Bakery("   ", "Sweet Treats", BakeryStatus.open, "123 Main St");
        });

        // Blank ID without spaces check
        assertThrows(IllegalArgumentException.class, () -> {
            new Bakery("", "Sweet Treats", BakeryStatus.open, "123 Main St");
        });
    }

    @Test
    void shouldPauseOpenBakeryOnlyOnce() {
        Bakery bakery = new Bakery(
                "bakery-123", "Sweet Treats", BakeryStatus.open, "123 Main St");

        assertTrue(bakery.pause());
        assertEquals(BakeryStatus.paused, bakery.getBakeryStatus());

        // Repeating an already-applied command is successful but is not a transition.
        assertFalse(bakery.pause());
        assertEquals(BakeryStatus.paused, bakery.getBakeryStatus());
    }

    @Test
    void shouldReopenPausedBakeryOnlyOnce() {
        Bakery bakery = new Bakery(
                "bakery-123", "Sweet Treats", BakeryStatus.paused, "123 Main St");

        assertTrue(bakery.reopen());
        assertEquals(BakeryStatus.open, bakery.getBakeryStatus());

        assertFalse(bakery.reopen());
        assertEquals(BakeryStatus.open, bakery.getBakeryStatus());
    }

    @Test
    void shouldNeverPauseOrReopenClosedBakery() {
        Bakery bakery = new Bakery(
                "bakery-123", "Sweet Treats", BakeryStatus.closed, "123 Main St");

        assertThrows(IllegalStateException.class, bakery::pause);
        assertThrows(IllegalStateException.class, bakery::reopen);
        // Both rejected commands leave the permanent state untouched.
        assertEquals(BakeryStatus.closed, bakery.getBakeryStatus());
    }
    
}
