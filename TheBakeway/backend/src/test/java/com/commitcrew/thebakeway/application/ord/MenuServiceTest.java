package com.commitcrew.thebakeway.application.ord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.commitcrew.thebakeway.domain.menu.MenuItem;
import com.commitcrew.thebakeway.domain.menu.MenuItemRegistry;
import com.commitcrew.thebakeway.domain.menu.MenuItemStatus;
import com.commitcrew.thebakeway.domain.bakery.Bakery;
import com.commitcrew.thebakeway.domain.bakery.BakeryStatus;

public class MenuServiceTest {

    @Test
    void shouldReturnAvailableMenuItem() {
        // Arrange: use a real service with a mock repository, so no Spring context
        // or database connection is needed to test the lookup behavior.
        MenuItemRegistry menuItemRegistry = mock(MenuItemRegistry.class);
        BakeryService bakeryService = mock(BakeryService.class);
        MenuService menuService = new MenuService(menuItemRegistry, bakeryService); 
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
        BakeryService bakeryService = mock(BakeryService.class);
        MenuService menuService = new MenuService(menuItemRegistry, bakeryService);
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
        BakeryService bakeryService = mock(BakeryService.class);
        MenuService menuService = new MenuService(menuItemRegistry, bakeryService);
        when(menuItemRegistry.findById("missing-id")).thenReturn(Optional.empty());

        // Act and assert: assertThrows fails if the service does not throw the expected
        // exception, and returns that exception so we can also inspect its message.
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> menuService.getMenuItem("missing-id"));

        assertEquals("Menu item not found with ID: missing-id", exception.getMessage());
        verify(menuItemRegistry).findById("missing-id");
    }

    @Test 
    void shouldReturnMenuIncludingSoldOutItemsForOpenBakery(){
        // Arrange: a bakery must be open to allow browsing its menu.
        MenuItemRegistry menuItemRegistry = mock(MenuItemRegistry.class);
        BakeryService bakeryService = mock(BakeryService.class);
        MenuService menuService = new MenuService(menuItemRegistry, bakeryService);

        Bakery bakery = new Bakery("bakery-123", "Sweet Treats", BakeryStatus.open, "123 Main St");
        when(bakeryService.getBakeryById("bakery-123")).thenReturn(bakery);

        MenuItem item1 = new MenuItem("bakery-123", "Croissant", "Butter croissant", 3.50,
                MenuItemStatus.available);
        MenuItem item2 = new MenuItem("bakery-123", "Baguette", "French baguette", 4.00,
                MenuItemStatus.sold_out);
        when(menuItemRegistry.findByBakeryId("bakery-123")).thenReturn(List.of(item1, item2));

        // Act: request the menu for browsing.
        List<MenuItem> actualMenu = menuService.getMenuForBrowsing("bakery-123");

        // Assert: the service returns all items, including sold-out ones.
        assertEquals(2, actualMenu.size());
        assertSame(item1, actualMenu.get(0));
        assertSame(item2, actualMenu.get(1));
        assertEquals(MenuItemStatus.sold_out, actualMenu.get(1).getStatus());
        verify(bakeryService).getBakeryById("bakery-123");
        verify(menuItemRegistry).findByBakeryId("bakery-123");

    }
    @Test
    void shouldReturnEmptyMenuForOpenBakery() {
        // An existing open bakery with no items is a valid browsing result.
        MenuItemRegistry menuItemRegistry = mock(MenuItemRegistry.class);
        BakeryService bakeryService = mock(BakeryService.class);
        MenuService menuService = new MenuService(menuItemRegistry, bakeryService);
        Bakery bakery = new Bakery("bakery-123", "Sweet Treats", BakeryStatus.open, "123 Main St");
        when(bakeryService.getBakeryById("bakery-123")).thenReturn(bakery);
        when(menuItemRegistry.findByBakeryId("bakery-123")).thenReturn(List.of());

        List<MenuItem> actualMenu = menuService.getMenuForBrowsing("bakery-123");

        assertEquals(List.of(), actualMenu);
        verify(bakeryService).getBakeryById("bakery-123");
        verify(menuItemRegistry).findByBakeryId("bakery-123");
    }

    @Test
    void shouldRejectBrowsingWhenBakeryDoesNotExist() {
        MenuItemRegistry menuItemRegistry = mock(MenuItemRegistry.class);
        BakeryService bakeryService = mock(BakeryService.class);
        MenuService menuService = new MenuService(menuItemRegistry, bakeryService);

        // We mock the service boundary: BakeryService already handles missing IDs.
        when(bakeryService.getBakeryById("missing-id"))
                .thenThrow(new BakeryNotFoundException("Bakery not found with ID: missing-id"));

        BakeryNotFoundException exception = assertThrows(BakeryNotFoundException.class,
                () -> menuService.getMenuForBrowsing("missing-id"));

        assertEquals("Bakery not found with ID: missing-id", exception.getMessage());
        verify(bakeryService).getBakeryById("missing-id");
        // A missing bakery must not be mistaken for a bakery with an empty menu.
        verifyNoInteractions(menuItemRegistry);
    }

    @Test
    void shouldRejectBrowsingForPausedBakery() {
        MenuItemRegistry menuItemRegistry = mock(MenuItemRegistry.class);
        BakeryService bakeryService = mock(BakeryService.class);
        MenuService menuService = new MenuService(menuItemRegistry, bakeryService);
        Bakery bakery = new Bakery("bakery-123", "Sweet Treats", BakeryStatus.paused, "123 Main St");
        when(bakeryService.getBakeryById("bakery-123")).thenReturn(bakery);

        BakeryUnavailableException exception = assertThrows(BakeryUnavailableException.class,
                () -> menuService.getMenuForBrowsing("bakery-123"));

        assertEquals("Bakery bakery-123 is unavailable for browsing: paused", exception.getMessage());
        verify(bakeryService).getBakeryById("bakery-123");
        // Availability must be checked before any menu query is attempted.
        verifyNoInteractions(menuItemRegistry);
    }

    @Test
    void shouldRejectBrowsingForClosedBakery() {
        MenuItemRegistry menuItemRegistry = mock(MenuItemRegistry.class);
        BakeryService bakeryService = mock(BakeryService.class);
        MenuService menuService = new MenuService(menuItemRegistry, bakeryService);
        Bakery bakery = new Bakery("bakery-123", "Sweet Treats", BakeryStatus.closed, "123 Main St");
        when(bakeryService.getBakeryById("bakery-123")).thenReturn(bakery);

        BakeryUnavailableException exception = assertThrows(BakeryUnavailableException.class,
                () -> menuService.getMenuForBrowsing("bakery-123"));

        assertEquals("Bakery bakery-123 is unavailable for browsing: closed", exception.getMessage());
        verify(bakeryService).getBakeryById("bakery-123");
        // Closed bakeries are also rejected without loading their menu items.
        verifyNoInteractions(menuItemRegistry);
    }
}
