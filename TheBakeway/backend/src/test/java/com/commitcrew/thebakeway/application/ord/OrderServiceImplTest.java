package com.commitcrew.thebakeway.application.ord;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.commitcrew.thebakeway.application.dlv.DispatchService;
import com.commitcrew.thebakeway.application.dlv.CourierDeliveryRecorder;
import com.commitcrew.thebakeway.application.reg.SessionManager;
import com.commitcrew.thebakeway.domain.account.AccountRegistry;
import com.commitcrew.thebakeway.domain.courier.CourierRegistry;
import com.commitcrew.thebakeway.domain.event.EventRepository;
import com.commitcrew.thebakeway.domain.bakery.Bakery;
import com.commitcrew.thebakeway.domain.bakery.BakeryStatus;
import com.commitcrew.thebakeway.domain.menu.MenuItem;
import com.commitcrew.thebakeway.domain.menu.MenuItemStatus;
import com.commitcrew.thebakeway.domain.order.OrderRegistry;

class OrderServiceImplTest {

    @Test
    void addMenuItemUsesBakeryDerivedFromAuthenticatedStaff() {
        BakeryService bakeryService = mock(BakeryService.class);
        MenuService menuService = mock(MenuService.class);
        BakeryStaffAccessService accessService = mock(BakeryStaffAccessService.class);
        OrderServiceImpl service = new OrderServiceImpl(
                bakeryService, menuService, accessService, mock(BakeryStatusAccessService.class),
                mock(OrderRegistry.class), mock(AccountRegistry.class), mock(SessionManager.class),
                mock(PreparationQueue.class), new TransitionPublisher(),
                mock(InitialStateService.class), mock(EventRepository.class), mock(CourierRegistry.class),
                mock(DispatchService.class), mock(CourierDeliveryRecorder.class));
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
        OrderServiceImpl service = new OrderServiceImpl(
                bakeryService, menuService, accessService, mock(BakeryStatusAccessService.class),
                mock(OrderRegistry.class), mock(AccountRegistry.class), mock(SessionManager.class),
                mock(PreparationQueue.class), new TransitionPublisher(),
                mock(InitialStateService.class), mock(EventRepository.class), mock(CourierRegistry.class),
                mock(DispatchService.class), mock(CourierDeliveryRecorder.class));
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
        OrderServiceImpl service = new OrderServiceImpl(
                bakeryService, menuService, accessService, mock(BakeryStatusAccessService.class),
                mock(OrderRegistry.class), mock(AccountRegistry.class), mock(SessionManager.class),
                mock(PreparationQueue.class), new TransitionPublisher(),
                mock(InitialStateService.class), mock(EventRepository.class), mock(CourierRegistry.class),
                mock(DispatchService.class), mock(CourierDeliveryRecorder.class));
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

    @Test
    void pauseUsesBakeryTargetAuthorizedForSession() {
        BakeryService bakeryService = mock(BakeryService.class);
        MenuService menuService = mock(MenuService.class);
        BakeryStaffAccessService staffAccess = mock(BakeryStaffAccessService.class);
        BakeryStatusAccessService statusAccess = mock(BakeryStatusAccessService.class);
        OrderServiceImpl service = new OrderServiceImpl(
                bakeryService, menuService, staffAccess, statusAccess,
                mock(OrderRegistry.class), mock(AccountRegistry.class), mock(SessionManager.class),
                mock(PreparationQueue.class), new TransitionPublisher(),
                mock(InitialStateService.class), mock(EventRepository.class), mock(CourierRegistry.class),
                mock(DispatchService.class), mock(CourierDeliveryRecorder.class));
        Bakery expected = new Bakery(
                "bakery-1", "Sweet Treats", BakeryStatus.paused, "123 Main St");
        when(statusAccess.resolvePauseTarget("session-1", "bakery-1"))
                .thenReturn("bakery-1");
        when(bakeryService.pauseBakery("bakery-1")).thenReturn(expected);

        assertSame(expected, service.pauseBakery("session-1", "bakery-1"));
        verify(statusAccess).resolvePauseTarget("session-1", "bakery-1");
        verify(bakeryService).pauseBakery("bakery-1");
    }

    @Test
    void reopenUsesBakeryTargetAuthorizedForOperatorSession() {
        BakeryService bakeryService = mock(BakeryService.class);
        MenuService menuService = mock(MenuService.class);
        BakeryStaffAccessService staffAccess = mock(BakeryStaffAccessService.class);
        BakeryStatusAccessService statusAccess = mock(BakeryStatusAccessService.class);
        OrderServiceImpl service = new OrderServiceImpl(
                bakeryService, menuService, staffAccess, statusAccess,
                mock(OrderRegistry.class), mock(AccountRegistry.class), mock(SessionManager.class),
                mock(PreparationQueue.class), new TransitionPublisher(),
                mock(InitialStateService.class), mock(EventRepository.class), mock(CourierRegistry.class),
                mock(DispatchService.class), mock(CourierDeliveryRecorder.class));
        Bakery expected = new Bakery(
                "bakery-1", "Sweet Treats", BakeryStatus.open, "123 Main St");
        when(statusAccess.resolveReopenTarget("session-1", "bakery-1"))
                .thenReturn("bakery-1");
        when(bakeryService.reopenBakery("bakery-1")).thenReturn(expected);

        assertSame(expected, service.reopenBakery("session-1", "bakery-1"));
        verify(statusAccess).resolveReopenTarget("session-1", "bakery-1");
        verify(bakeryService).reopenBakery("bakery-1");
    }
}
