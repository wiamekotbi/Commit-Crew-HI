package com.commitcrew.thebakeway.application.ord;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.commitcrew.thebakeway.application.reg.SessionManager;
import com.commitcrew.thebakeway.domain.account.AccountRegistry;
import com.commitcrew.thebakeway.domain.account.BakewayAccount;
import com.commitcrew.thebakeway.domain.account.Role;
import com.commitcrew.thebakeway.domain.bakery.Bakery;
import com.commitcrew.thebakeway.domain.event.EntityType;
import com.commitcrew.thebakeway.domain.event.StateTransitionEvent;
import com.commitcrew.thebakeway.domain.menu.MenuItem;
import com.commitcrew.thebakeway.domain.menu.MenuItemStatus;
import com.commitcrew.thebakeway.domain.order.Order;
import com.commitcrew.thebakeway.domain.order.OrderRegistry;
import com.commitcrew.thebakeway.domain.session.Session;

@Service("orderServiceImpl")
public class OrderServiceImpl implements OrderService {

    private final BakeryService bakeryService;
    private final MenuService menuService;
    private final BakeryStaffAccessService bakeryStaffAccessService;
    private final BakeryStatusAccessService bakeryStatusAccessService;
    private final OrderRegistry orderRegistry;
    private final AccountRegistry accountRegistry;
    private final SessionManager sessionManager;
    private final PreparationQueue preparationQueue;
    private final TransitionPublisher transitionPublisher;

    // Reuse the existing retrieval services rather than duplicating their queries and rules.
    public OrderServiceImpl(
            BakeryService bakeryService,
            MenuService menuService,
            BakeryStaffAccessService bakeryStaffAccessService,
            BakeryStatusAccessService bakeryStatusAccessService,
            OrderRegistry orderRegistry,
            AccountRegistry accountRegistry,
            SessionManager sessionManager,
            PreparationQueue preparationQueue,
            TransitionPublisher transitionPublisher) {
        this.bakeryService = bakeryService;
        this.menuService = menuService;
        this.bakeryStaffAccessService = bakeryStaffAccessService;
        this.bakeryStatusAccessService = bakeryStatusAccessService;
        this.orderRegistry = orderRegistry;
        this.accountRegistry = accountRegistry;
        this.sessionManager = sessionManager;
        this.preparationQueue = preparationQueue;
        this.transitionPublisher = transitionPublisher;
    }

    // Creates a new order for the logged-in customer at an open bakery. Every item must
    // exist, belong to that bakery and be orderable; otherwise nothing is saved.
    // Returns the id of the new "placed" order.
    @Override
    @Transactional
    public String placeOrder(String sessionId, PlaceOrderRequest request) {
        BakewayAccount customer = currentAccount(sessionId);
        validateRequest(request);
        bakeryService.requireOpenForOrdering(request.bakeryId());

        Order order = new Order(
                customer.getAccountId(),
                request.bakeryId(),
                customer.getDisplayName(),
                deliveryAddress(request, customer));

        // Order.addOrderItem refuses sold-out items
        for (OrderItemRequest item : request.items()) {
            order.addOrderItem(orderableMenuItem(request.bakeryId(), item.menuItemId()), item.quantity());
        }

        return saveAndPublish(order, null).getId();
    }

    // Cancels an order. A customer can cancel only their own order while it is placed;
    // an operator can cancel any order up to ready (the State classes enforce this).
    @Override
    @Transactional
    public void cancelOrder(String sessionId, String orderId) {
        Session session = currentSession(sessionId);
        Order order = existingOrder(orderId);

        // Customers only see their own orders; operators may cancel any order
        if (session.getRole() == Role.CUSTOMER && !order.getCustomerId().equals(session.getAccountId())) {
            throw new IllegalArgumentException("Order not found");
        }

        String previousState = order.getStatus();

        // Leaving "accepted" also removes the order from the preparation queue
        order.cancel(session.getRole());

        saveAndPublish(order, previousState);
    }

    // Bakery staff accept a placed order of their own bakery.
    @Override
    @Transactional
    public void acceptOrder(String sessionId, String orderId) {
        Order order = staffOrder(sessionId, orderId);
        String previousState = order.getStatus();

        // An accepted order joins its bakery's preparation queue
        order.accept();

        saveAndPublish(order, previousState);
    }

    // Bakery staff reject a placed order of their own bakery; a non-blank reason is required and stored.
    @Override
    @Transactional
    public void rejectOrder(String sessionId, String orderId, String reason) {
        Order order = staffOrder(sessionId, orderId);
        String previousState = order.getStatus();

        order.reject(reason);

        saveAndPublish(order, previousState);
    }

    // Bakery staff start preparing an accepted order; only the first order in their
    // bakery's preparation queue (oldest accepted order) may start.
    @Override
    @Transactional
    public void startPreparing(String sessionId, String orderId) {
        Order order = staffOrder(sessionId, orderId);
        String previousState = order.getStatus();

        // Checked before the state changes so a refused order stays untouched
        if (!isFirstInQueue(order)) {
            throw new IllegalArgumentException("Order is not first in its bakery's preparation queue");
        }

        order.startPreparing();

        saveAndPublish(order, previousState);
    }

    // Bakery staff mark an order of their own bakery as ready once it has been prepared.
    @Override
    @Transactional
    public void markReady(String sessionId, String orderId) {
        Order order = staffOrder(sessionId, orderId);
        String previousState = order.getStatus();

        order.markReady();

        saveAndPublish(order, previousState);
    }

    @Override
    public List<Bakery> getOpenBakeries(String sessionId) {
        // Customer-facing calls reach this method through the proxy's access checks.
        return bakeryService.getOpenBakeries();
    }

    @Override
    public List<MenuItem> getMenuForBrowsing(String sessionId, String bakeryId) {
        // MenuService checks bakery availability and preserves sold-out items in the result.
        return menuService.getMenuForBrowsing(bakeryId);
    }

    @Override
    public MenuItem addMenuItem(String sessionId, String name, String description, double price) {
        // Ownership comes from the authenticated account, never from request data.
        String bakeryId = bakeryStaffAccessService.getBakeryIdForSession(sessionId);
        return menuService.addMenuItem(bakeryId, name, description, price);
    }

    @Override
    public MenuItem editMenuItem(
            String sessionId, String itemId, String name, String description, double price) {
        String bakeryId = bakeryStaffAccessService.getBakeryIdForSession(sessionId);
        return menuService.editMenuItem(bakeryId, itemId, name, description, price);
    }

    @Override
    public MenuItem updateMenuItemAvailability(
            String sessionId, String itemId, MenuItemStatus newStatus) {
        // Resolve ownership from the session for every mutation. The caller
        // cannot claim access by placing an arbitrary bakery ID in a request.
        String bakeryId = bakeryStaffAccessService.getBakeryIdForSession(sessionId);
        return menuService.updateMenuItemAvailability(bakeryId, itemId, newStatus);
    }

    @Override
    public Bakery pauseBakery(String sessionId, String bakeryId) {
        // Resolve the authorized target before loading or mutating its entity.
        // For restaurant accounts, this compares the request with persistent ownership.
        String authorizedBakeryId = bakeryStatusAccessService.resolvePauseTarget(
                sessionId, bakeryId);
        return bakeryService.pauseBakery(authorizedBakeryId);
    }

    @Override
    public Bakery reopenBakery(String sessionId, String bakeryId) {
        // The access service provides defense in depth in addition to the proxy:
        // only an authenticated operator can reach the domain transition.
        String authorizedBakeryId = bakeryStatusAccessService.resolveReopenTarget(
                sessionId, bakeryId);
        return bakeryService.reopenBakery(authorizedBakeryId);
    }

    // Returns the caller's session, or refuses the command if they are not logged in.
    private Session currentSession(String sessionId) {
        return sessionManager.findSession(sessionId)
                .orElseThrow(() -> new IllegalStateException("Authentication required"));
    }

    // Loads the account of the logged-in caller.
    private BakewayAccount currentAccount(String sessionId) {
        return accountRegistry.findById(currentSession(sessionId).getAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    // Loads an order for a staff command; staff only act on orders of the bakery their
    // account belongs to, so another bakery's order is reported as not found.
    private Order staffOrder(String sessionId, String orderId) {
        String staffBakeryId = bakeryStaffAccessService.getBakeryIdForSession(sessionId);
        Order order = existingOrder(orderId);

        if (!order.getBakeryId().equals(staffBakeryId)) {
            throw new IllegalArgumentException("Order not found");
        }

        return order;
    }

    // Loads an order by id, or refuses the command if it does not exist.
    private Order existingOrder(String orderId) {
        return orderRegistry.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
    }

    // True when the order is the oldest accepted order of its bakery's preparation queue.
    private boolean isFirstInQueue(Order order) {
        List<Order> queue = preparationQueue.getPreparationQueue(order.getBakeryId());
        return !queue.isEmpty() && queue.get(0).getId().equals(order.getId());
    }

    // Checks the request itself: a bakery, at least one item, positive quantities, no repeated item.
    private void validateRequest(PlaceOrderRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Order request is required");
        }

        if (request.bakeryId() == null || request.bakeryId().isBlank()) {
            throw new IllegalArgumentException("Bakery is required");
        }

        List<OrderItemRequest> items = request.items();
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("An order needs at least one item");
        }

        Set<String> menuItemIds = new HashSet<>();
        for (OrderItemRequest item : items) {
            if (item == null || item.menuItemId() == null || item.menuItemId().isBlank()) {
                throw new IllegalArgumentException("Menu item is required");
            }

            if (item.quantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be a positive integer");
            }

            if (!menuItemIds.add(item.menuItemId())) {
                throw new IllegalArgumentException("Menu item appears more than once: " + item.menuItemId());
            }
        }
    }

    // Loads a menu item and checks it belongs to the ordered bakery and is still offered.
    private MenuItem orderableMenuItem(String bakeryId, String menuItemId) {
        MenuItem menuItem = menuService.getMenuItem(menuItemId);

        if (!bakeryId.equals(menuItem.getBakeryId())) {
            throw new IllegalArgumentException("Menu item does not belong to this bakery: " + menuItemId);
        }

        if (menuItem.isDiscontinued()) {
            throw new IllegalArgumentException("Menu item is no longer offered: " + menuItem.getName());
        }

        return menuItem;
    }

    // Uses the address from the request, or the customer's profile address when none is given.
    private String deliveryAddress(PlaceOrderRequest request, BakewayAccount customer) {
        String address = request.deliveryAddress();
        if (address == null || address.isBlank()) {
            address = customer.getDeliveryAddress();
        }

        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("Delivery address is required");
        }

        return address;
    }

    // Saves first, then publishes exactly one event in the same transaction;
    // an observer failure rolls the state change back.
    private Order saveAndPublish(Order order, String previousState) {
        Order saved = orderRegistry.save(order);
        transitionPublisher.notifyObservers(new StateTransitionEvent(
                UUID.randomUUID().toString(),
                EntityType.ORDER,
                saved.getId(),
                previousState,
                saved.getStatus(),
                Instant.now()));
        return saved;
    }
}
