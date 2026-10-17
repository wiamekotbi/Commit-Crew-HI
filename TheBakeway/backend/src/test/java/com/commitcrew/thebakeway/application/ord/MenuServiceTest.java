package com.commitcrew.thebakeway.application.ord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.commitcrew.thebakeway.domain.menu.MenuItem;
import com.commitcrew.thebakeway.domain.menu.MenuItemRegistry;
import com.commitcrew.thebakeway.domain.menu.MenuItemStatus;

public class MenuServiceTest {

    @Test
    void shouldReturnAvailableMenuItem() {
        // Arrange: use a real service with a mock repository, so no Spring context
        // or database connection is needed to test the lookup behavior.
        MenuItemRegistry menuItemRegistry = mock(MenuItemRegistry.class);
        MenuService menuService = new MenuService(menuItemRegistry);
        MenuItem item = new MenuItem("bakery-123", "Croissant", "Butter croissant", 3.50,
                MenuItemStatus.available);

        // JPA normally generates the item's ID. Here we control the lookup response
        // directly; ID generation and database mapping are outside this unit test.
        when(menuItemRegistry.findById("item-123")).thenReturn(Optional.of(item));

        // Act: request an item through the service's public lookup method.
        MenuItem actualItem = menuService.getMenuItem("item-123");

        // Assert: return the same item supplied by the repository, with its status intact.
        assertSame(item, actualItem);
        assertEquals(MenuItemStatus.available, actualItem.getStatus());
        verify(menuItemRegistry).findById("item-123");
    }

    @Test
    void shouldReturnSoldOutMenuItem() {
        // Arrange: a sold-out item still exists and must remain retrievable.
        MenuItemRegistry menuItemRegistry = mock(MenuItemRegistry.class);
        MenuService menuService = new MenuService(menuItemRegistry);
        MenuItem item = new MenuItem("bakery-123", "Baguette", "French baguette", 4.00,
                MenuItemStatus.sold_out);
        when(menuItemRegistry.findById("item-456")).thenReturn(Optional.of(item));

        // Act: lookup retrieves data; order-placement logic decides if it can be ordered.
        MenuItem actualItem = menuService.getMenuItem("item-456");

        // Assert: lookup neither rejects the item nor changes its availability.
        assertSame(item, actualItem);
        assertEquals(MenuItemStatus.sold_out, actualItem.getStatus());
        verify(menuItemRegistry).findById("item-456");
    }

    @Test
    void shouldThrowExceptionWhenMenuItemDoesNotExist() {
        // Arrange: Optional.empty() represents a lookup that found no matching item.
        MenuItemRegistry menuItemRegistry = mock(MenuItemRegistry.class);
        MenuService menuService = new MenuService(menuItemRegistry);
        when(menuItemRegistry.findById("missing-id")).thenReturn(Optional.empty());

        // Act and assert: assertThrows fails if the service does not throw the expected
        // exception, and returns that exception so we can also inspect its message.
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> menuService.getMenuItem("missing-id"));

        assertEquals("Menu item not found with ID: missing-id", exception.getMessage());
        verify(menuItemRegistry).findById("missing-id");
    }
}
