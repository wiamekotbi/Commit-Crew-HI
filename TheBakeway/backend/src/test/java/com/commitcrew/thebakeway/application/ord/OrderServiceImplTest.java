package com.commitcrew.thebakeway.application.ord;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.commitcrew.thebakeway.domain.menu.MenuItem;
import com.commitcrew.thebakeway.domain.menu.MenuItemStatus;

class OrderServiceImplTest {

    @Test
    void addMenuItemUsesBakeryDerivedFromAuthenticatedStaff() {
        BakeryService bakeryService = mock(BakeryService.class);
        MenuService menuService = mock(MenuService.class);
        BakeryStaffAccessService accessService = mock(BakeryStaffAccessService.class);
        OrderServiceImpl service = new OrderServiceImpl(bakeryService, menuService, accessService);
        MenuItem expected = new MenuItem(
                "bakery-1", "Croissant", "Butter croissant", 3.50, MenuItemStatus.available);
        when(accessService.getBakeryIdForSession("session-1")).thenReturn("bakery-1");
        when(menuService.addMenuItem("bakery-1", "Croissant", "Butter croissant", 3.50))
                .thenReturn(expected);

        MenuItem actual = service.addMenuItem(
                "session-1", "Croissant", "Butter croissant", 3.50);

        assertSame(expected, actual);
        verify(accessService).getBakeryIdForSession("session-1");
        verify(menuService).addMenuItem("bakery-1", "Croissant", "Butter croissant", 3.50);
    }

    @Test
    void editMenuItemUsesBakeryDerivedFromAuthenticatedStaff() {
        BakeryService bakeryService = mock(BakeryService.class);
        MenuService menuService = mock(MenuService.class);
        BakeryStaffAccessService accessService = mock(BakeryStaffAccessService.class);
        OrderServiceImpl service = new OrderServiceImpl(bakeryService, menuService, accessService);
        MenuItem expected = new MenuItem(
                "bakery-1", "Updated", "New description", 4.00, MenuItemStatus.available);
        when(accessService.getBakeryIdForSession("session-1")).thenReturn("bakery-1");
        when(menuService.editMenuItem(
                "bakery-1", "item-1", "Updated", "New description", 4.00))
                .thenReturn(expected);

        MenuItem actual = service.editMenuItem(
                "session-1", "item-1", "Updated", "New description", 4.00);

        assertSame(expected, actual);
        verify(accessService).getBakeryIdForSession("session-1");
        verify(menuService).editMenuItem(
                "bakery-1", "item-1", "Updated", "New description", 4.00);
    }

    @Test
    void availabilityChangeUsesBakeryDerivedFromAuthenticatedStaff() {
        BakeryService bakeryService = mock(BakeryService.class);
        MenuService menuService = mock(MenuService.class);
        BakeryStaffAccessService accessService = mock(BakeryStaffAccessService.class);
        OrderServiceImpl service = new OrderServiceImpl(bakeryService, menuService, accessService);
        MenuItem expected = new MenuItem(
                "bakery-1", "Croissant", "Butter croissant", 3.50,
                MenuItemStatus.sold_out);
        when(accessService.getBakeryIdForSession("session-1")).thenReturn("bakery-1");
        when(menuService.updateMenuItemAvailability(
                "bakery-1", "item-1", MenuItemStatus.sold_out)).thenReturn(expected);

        MenuItem actual = service.updateMenuItemAvailability(
                "session-1", "item-1", MenuItemStatus.sold_out);

        assertSame(expected, actual);
        // The implementation forwards the trusted bakery association resolved
        // from the session rather than accepting ownership from the caller.
        verify(accessService).getBakeryIdForSession("session-1");
        verify(menuService).updateMenuItemAvailability(
                "bakery-1", "item-1", MenuItemStatus.sold_out);
    }
}
