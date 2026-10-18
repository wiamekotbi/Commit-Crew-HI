package com.commitcrew.thebakeway.application.ord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.commitcrew.thebakeway.application.dashboard.DashboardReadModel;
import com.commitcrew.thebakeway.application.dashboard.DashboardUpdater;
import com.commitcrew.thebakeway.domain.event.EntityType;
import com.commitcrew.thebakeway.domain.event.EventRepository;
import com.commitcrew.thebakeway.domain.event.StateTransitionEvent;
import com.commitcrew.thebakeway.domain.menu.MenuItem;
import com.commitcrew.thebakeway.domain.menu.MenuItemRegistry;
import com.commitcrew.thebakeway.domain.menu.MenuItemStatus;

class MenuTransitionObserverIntegrationTest {

    @Test
    void successfulAvailabilityChangeReachesRecorderAndDashboard() {
        MenuItemRegistry menuItemRegistry = mock(MenuItemRegistry.class);
        EventRepository eventRepository = mock(EventRepository.class);
        DashboardReadModel dashboard = new DashboardReadModel();

        // Use the real Observer collaborators to verify the complete in-process
        // path rather than repeating MenuService's isolated publisher test.
        EventRecorder recorder = new EventRecorder(eventRepository);
        DashboardUpdater dashboardUpdater = new DashboardUpdater(dashboard);
        TransitionPublisher publisher = new TransitionPublisher(
                List.of(recorder, dashboardUpdater));
        MenuService menuService = new MenuService(
                menuItemRegistry, mock(BakeryService.class), publisher);

        MenuItem item = menuItem(MenuItemStatus.available);
        when(menuItemRegistry.findById("item-1")).thenReturn(Optional.of(item));
        when(menuItemRegistry.save(item)).thenReturn(item);

        menuService.updateMenuItemAvailability(
                "bakery-1", "item-1", MenuItemStatus.sold_out);

        // One saved event and one projected state represent the same transition.
        verify(eventRepository).save(org.mockito.ArgumentMatchers.argThat(event ->
                event.getEntityType() == EntityType.MENU_ITEM
                        && event.getEntityId().equals("item-1")
                        && event.getPreviousState().equals("available")
                        && event.getNewState().equals("sold_out")));
        assertEquals("sold_out",
                dashboard.findState(EntityType.MENU_ITEM, "item-1").orElseThrow());
    }

    @Test
    void unchangedAvailabilityDoesNotReachAnyObserver() {
        MenuItemRegistry menuItemRegistry = mock(MenuItemRegistry.class);
        EventRepository eventRepository = mock(EventRepository.class);
        DashboardReadModel dashboard = new DashboardReadModel();
        TransitionPublisher publisher = new TransitionPublisher(List.of(
                new EventRecorder(eventRepository),
                new DashboardUpdater(dashboard)));
        MenuService menuService = new MenuService(
                menuItemRegistry, mock(BakeryService.class), publisher);

        MenuItem item = menuItem(MenuItemStatus.sold_out);
        when(menuItemRegistry.findById("item-1")).thenReturn(Optional.of(item));

        menuService.updateMenuItemAvailability(
                "bakery-1", "item-1", MenuItemStatus.sold_out);

        verify(menuItemRegistry, never()).save(item);
        verify(eventRepository, never()).save(
                org.mockito.ArgumentMatchers.any(StateTransitionEvent.class));
        assertEquals(Optional.empty(),
                dashboard.findState(EntityType.MENU_ITEM, "item-1"));
    }

    private MenuItem menuItem(MenuItemStatus status) {
        MenuItem item = new MenuItem(
                "bakery-1", "Croissant", "Butter croissant", 3.50, status);
        ReflectionTestUtils.setField(item, "id", "item-1");
        return item;
    }
}
