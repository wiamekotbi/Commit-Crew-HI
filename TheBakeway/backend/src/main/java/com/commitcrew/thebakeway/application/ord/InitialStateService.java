package com.commitcrew.thebakeway.application.ord;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.Scanner;

import jakarta.persistence.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.commitcrew.thebakeway.application.console.CommandConsole;
import com.commitcrew.thebakeway.application.dashboard.DashboardReadModel;
import com.commitcrew.thebakeway.application.dlv.ActiveDispatchOffers;
import com.commitcrew.thebakeway.application.dlv.DispatchWaitingQueue;
import com.commitcrew.thebakeway.application.reg.AccountSeedingService;
import com.commitcrew.thebakeway.application.reg.SessionManager;
import com.commitcrew.thebakeway.domain.account.AccountRegistry;
import com.commitcrew.thebakeway.domain.account.BakewayAccount;
import com.commitcrew.thebakeway.domain.account.Role;
import com.commitcrew.thebakeway.domain.bakery.BakeryRegistry;
import com.commitcrew.thebakeway.domain.courier.CourierRegistry;
import com.commitcrew.thebakeway.domain.courier.CourierDeliveryRepository;
import com.commitcrew.thebakeway.domain.event.EventRepository;
import com.commitcrew.thebakeway.domain.menu.MenuItemRegistry;
import com.commitcrew.thebakeway.domain.order.OrderRegistry;
import com.commitcrew.thebakeway.domain.pricing.OrderLedgerRepository;
import com.commitcrew.thebakeway.domain.rating.CourierRatingRepository;

// Loads the initial state from the config files, at startup and when the operator restores it
// (UC-REG-03, UC-ORD-01). Everything happens in one transaction: on any failure the previous state is kept.
@Service
public class InitialStateService {

    static final String CITY_FILE = "config/initialCity.txt";
    static final String ACCOUNTS_FILE = "config/initialAccounts.txt";

    private static final Logger logger = LoggerFactory.getLogger(InitialStateService.class);

    private final CityConfigurationReader cityConfigurationReader;
    private final OrderRegistry orderRegistry;
    private final OrderLedgerRepository orderLedgerRepository;
    private final EventRepository eventRepository;
    private final MenuItemRegistry menuItemRegistry;
    private final BakeryRegistry bakeryRegistry;
    private final CourierRegistry courierRegistry;
    private final CourierDeliveryRepository courierDeliveryRepository;
    private final CourierRatingRepository courierRatingRepository;
    private final AccountRegistry accountRegistry;
    private final PasswordEncoder passwordEncoder;
    private final SessionManager sessionManager;
    private final DashboardReadModel dashboardReadModel;
    private final DispatchWaitingQueue dispatchWaitingQueue;
    private final ActiveDispatchOffers activeDispatchOffers;
    private final CommandConsole console;
    private final EntityManager entityManager;

    public InitialStateService(
            CityConfigurationReader cityConfigurationReader,
            OrderRegistry orderRegistry,
            OrderLedgerRepository orderLedgerRepository,
            EventRepository eventRepository,
            MenuItemRegistry menuItemRegistry,
            BakeryRegistry bakeryRegistry,
            CourierRegistry courierRegistry,
            CourierDeliveryRepository courierDeliveryRepository,
            CourierRatingRepository courierRatingRepository,
            AccountRegistry accountRegistry,
            PasswordEncoder passwordEncoder,
            SessionManager sessionManager,
            DashboardReadModel dashboardReadModel,
            DispatchWaitingQueue dispatchWaitingQueue,
            ActiveDispatchOffers activeDispatchOffers,
            CommandConsole console,
            EntityManager entityManager) {
        this.cityConfigurationReader = cityConfigurationReader;
        this.orderRegistry = orderRegistry;
        this.orderLedgerRepository = orderLedgerRepository;
        this.eventRepository = eventRepository;
        this.menuItemRegistry = menuItemRegistry;
        this.bakeryRegistry = bakeryRegistry;
        this.courierRegistry = courierRegistry;
        this.courierDeliveryRepository = courierDeliveryRepository;
        this.courierRatingRepository = courierRatingRepository;
        this.accountRegistry = accountRegistry;
        this.passwordEncoder = passwordEncoder;
        this.sessionManager = sessionManager;
        this.dashboardReadModel = dashboardReadModel;
        this.dispatchWaitingQueue = dispatchWaitingQueue;
        this.activeDispatchOffers = activeDispatchOffers;
        this.console = console;
        this.entityManager = entityManager;
    }

    // Replaces bakeries, menus, couriers and accounts with the config files and
    // clears orders, their ledger and courier history, queues and events.
    // Sessions end (the account seeding invalidates them).
    @Transactional
    public InitialStateSummary loadInitialState() {
        try {
            InitialStateSummary summary = replaceState();
            console.record("system", "SYSTEM", "LOAD_INITIAL_STATE", true, summary.bakeries() + " bakeries, "
                    + summary.menuItems() + " menu items, accounts per role " + summary.accountsPerRole());
            return summary;
        } catch (RuntimeException e) {
            // UC-REG-03 / UC-ORD-01: the error names the bad entry; the transaction keeps the previous state
            console.record("system", "SYSTEM", "LOAD_INITIAL_STATE", false, e.getMessage());
            throw e;
        }
    }

    private InitialStateSummary replaceState() {
        // Read and check both files first, so a bad file changes nothing
        CityConfiguration city = cityConfigurationReader.read(scanner(CITY_FILE));
        String accounts = read(ACCOUNTS_FILE);

        // Ledger, ratings and courier history rows reference orders, so remove them first.
        orderLedgerRepository.deleteAllInBatch();
        courierRatingRepository.deleteAllInBatch();
        courierDeliveryRepository.deleteAllInBatch();
        orderRegistry.deleteAll();
        orderRegistry.flush();
        eventRepository.deleteAllInBatch();
        menuItemRegistry.deleteAllInBatch();
        // Courier rows contain assignment state from the previous city run and
        // must be replaced by the configured locations and clean lifecycle state.
        courierRegistry.deleteAllInBatch();
        // Accounts before bakeries: staff accounts reference their bakery
        accountRegistry.deleteAllInBatch();
        bakeryRegistry.deleteAllInBatch();
        // Deleting the orders loaded their menu items; forget them so the config rows are inserted fresh
        // instead of updating rows that no longer exist
        entityManager.clear();

        bakeryRegistry.saveAll(city.bakeries());
        bakeryRegistry.flush();
        menuItemRegistry.saveAll(city.menuItems());
        menuItemRegistry.flush();
        courierRegistry.saveAll(city.couriers());
        courierRegistry.flush();

        new AccountSeedingService(accountRegistry, passwordEncoder, sessionManager)
                .seedAccounts(new Scanner(accounts));
        accountRegistry.flush();

        Map<Role, Integer> accountsPerRole = accountsPerRole();
        if (accountsPerRole.size() < Role.values().length) {
            // R-REG-03: at least one account per role, otherwise the whole load is refused
            throw new InvalidConfigurationException(
                    "Account seeding failed: every role needs at least one valid account in " + ACCOUNTS_FILE);
        }

        dashboardReadModel.clear();
        // Reset also removes transient READY-order retry state. The orders were
        // deleted above, so retaining their queue IDs would create stale work.
        dispatchWaitingQueue.clear();
        activeDispatchOffers.clear();

        InitialStateSummary summary = new InitialStateSummary(
                city.bakeries().size(), city.menuItems().size(), accountsPerRole);
        logger.info("Initial state loaded: {}", summary);
        return summary;
    }

    private Map<Role, Integer> accountsPerRole() {
        Map<Role, Integer> counts = new EnumMap<>(Role.class);
        for (BakewayAccount account : accountRegistry.findAll()) {
            counts.merge(account.getRole(), 1, Integer::sum);
        }
        return counts;
    }

    private Scanner scanner(String path) {
        return new Scanner(read(path));
    }

    private String read(String path) {
        try (InputStream input = new ClassPathResource(path).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new InvalidConfigurationException("Config file not found or unreadable: " + path);
        }
    }
}
