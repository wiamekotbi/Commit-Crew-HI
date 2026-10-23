package com.commitcrew.thebakeway.application.ord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.commitcrew.thebakeway.application.dashboard.DashboardReadModel;
import com.commitcrew.thebakeway.application.reg.SessionManager;
import com.commitcrew.thebakeway.domain.account.AccountRegistry;
import com.commitcrew.thebakeway.domain.account.BakewayAccount;
import com.commitcrew.thebakeway.domain.account.Role;
import com.commitcrew.thebakeway.domain.bakery.Bakery;
import com.commitcrew.thebakeway.domain.bakery.BakeryRegistry;
import com.commitcrew.thebakeway.domain.bakery.BakeryStatus;
import com.commitcrew.thebakeway.domain.event.EventRepository;
import com.commitcrew.thebakeway.domain.menu.MenuItem;
import com.commitcrew.thebakeway.domain.menu.MenuItemRegistry;
import com.commitcrew.thebakeway.domain.menu.MenuItemStatus;
import com.commitcrew.thebakeway.domain.order.OrderRegistry;

@ExtendWith(MockitoExtension.class)
class InitialStateServiceTest {

    @Mock
    private CityConfigurationReader cityConfigurationReader;

    @Mock
    private OrderRegistry orderRegistry;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private MenuItemRegistry menuItemRegistry;

    @Mock
    private BakeryRegistry bakeryRegistry;

    @Mock
    private AccountRegistry accountRegistry;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private SessionManager sessionManager;

    @Mock
    private DashboardReadModel dashboardReadModel;

    private InitialStateService service;

    private final CityConfiguration city = new CityConfiguration(
            List.of(new Bakery("bakery-1", "Sweet Treats", BakeryStatus.open, "123 Main Street")),
            List.of(new MenuItem("item-1", "bakery-1", "Croissant", "", 3.50, MenuItemStatus.available)));

    @BeforeEach
    void setUp() {
        service = new InitialStateService(
                cityConfigurationReader,
                orderRegistry,
                eventRepository,
                menuItemRegistry,
                bakeryRegistry,
                accountRegistry,
                passwordEncoder,
                sessionManager,
                dashboardReadModel);
    }

    @Test
    void replacesEverythingWithTheConfigFiles() {
        when(cityConfigurationReader.read(any())).thenReturn(city);
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(accountRegistry.findAll()).thenReturn(oneAccountPerRole());

        InitialStateSummary summary = service.loadInitialState();

        assertEquals(1, summary.bakeries());
        assertEquals(1, summary.menuItems());
        assertEquals(Map.of(Role.CUSTOMER, 1, Role.RESTAURANT, 1, Role.COURIER, 1, Role.OPERATOR, 1),
                summary.accountsPerRole());

        // Orders and events are cleared, and rows are removed before the ones they reference
        InOrder order = inOrder(orderRegistry, eventRepository, menuItemRegistry, accountRegistry, bakeryRegistry,
                dashboardReadModel);
        order.verify(orderRegistry).deleteAll();
        order.verify(eventRepository).deleteAllInBatch();
        order.verify(menuItemRegistry).deleteAllInBatch();
        order.verify(accountRegistry).deleteAllInBatch();
        order.verify(bakeryRegistry).deleteAllInBatch();
        order.verify(bakeryRegistry).saveAll(city.bakeries());
        order.verify(menuItemRegistry).saveAll(city.menuItems());
        order.verify(accountRegistry).deleteAll();
        order.verify(dashboardReadModel).clear();

        // The shipped accounts file is seeded and every session ends
        verify(sessionManager).invalidateAllSessions();
    }

    @Test
    void invalidCityFileChangesNothing() {
        when(cityConfigurationReader.read(any()))
                .thenThrow(new InvalidConfigurationException("Invalid city config entry at line 3: price cannot be negative"));

        assertThrows(InvalidConfigurationException.class, () -> service.loadInitialState());

        verifyNoInteractions(orderRegistry, eventRepository, menuItemRegistry, bakeryRegistry, accountRegistry,
                sessionManager, dashboardReadModel);
    }

    @Test
    void refusesTheLoadWhenARoleHasNoAccount() {
        when(cityConfigurationReader.read(any())).thenReturn(city);
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(accountRegistry.findAll()).thenReturn(List.of(account("1", Role.CUSTOMER)));

        // The exception rolls the transaction back, so the previous state is kept
        assertThrows(InvalidConfigurationException.class, () -> service.loadInitialState());

        verify(dashboardReadModel, never()).clear();
    }

    private List<BakewayAccount> oneAccountPerRole() {
        return List.of(
                account("1", Role.CUSTOMER),
                account("2", Role.RESTAURANT),
                account("3", Role.COURIER),
                account("4", Role.OPERATOR));
    }

    private BakewayAccount account(String id, Role role) {
        return new BakewayAccount(id, "user" + id, "pw", "User " + id, "", role == Role.CUSTOMER ? "Montreal" : "",
                role, role == Role.RESTAURANT ? "bakery-1" : null);
    }
}
