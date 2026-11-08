package com.commitcrew.thebakeway.application.ord;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import com.commitcrew.thebakeway.application.dlv.DispatchService;
import com.commitcrew.thebakeway.application.reg.SessionManager;
import com.commitcrew.thebakeway.domain.account.AccountRegistry;
import com.commitcrew.thebakeway.domain.account.BakewayAccount;
import com.commitcrew.thebakeway.domain.account.Role;
import com.commitcrew.thebakeway.domain.bakery.Bakery;
import com.commitcrew.thebakeway.domain.courier.Courier;
import com.commitcrew.thebakeway.domain.courier.CourierRegistry;
import com.commitcrew.thebakeway.domain.event.EntityType;
import com.commitcrew.thebakeway.domain.event.EventRepository;
import com.commitcrew.thebakeway.domain.event.StateTransitionEvent;
import com.commitcrew.thebakeway.domain.menu.MenuItem;
import com.commitcrew.thebakeway.domain.menu.MenuItemStatus;
import com.commitcrew.thebakeway.domain.order.Order;
import com.commitcrew.thebakeway.domain.order.OrderLine;
import com.commitcrew.thebakeway.domain.order.OrderRegistry;
import com.commitcrew.thebakeway.domain.order.OrderStatus;
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
    private final InitialStateService initialStateService;
    private final EventRepository eventRepository;
    private final CourierRegistry courierRegistry;
    private final DispatchService dispatchService;

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
            TransitionPublisher transitionPublisher,
            InitialStateService initialStateService,
            EventRepository eventRepository,
            CourierRegistry courierRegistry,
            DispatchService dispatchService) {
        this.bakeryService = bakeryService;
        this.menuService = menuService;
        this.bakeryStaffAccessService = bakeryStaffAccessService;
        this.bakeryStatusAccessService = bakeryStatusAccessService;
        this.orderRegistry = orderRegistry;
        this.accountRegistry = accountRegistry;
        this.sessionManager = sessionManager;
        this.preparationQueue = preparationQueue;
        this.transitionPublisher = transitionPublisher;
        this.initialStateService = initialStateService;
        this.eventRepository = eventRepository;
        this.courierRegistry = courierRegistry;
        this.dispatchService = dispatchService;
    }

    // Creates a new order for the logged-in customer at an open bakery. Every item must
    // exist, belong to that bakery and be orderable; otherwise nothing is saved.
    // Returns the new order id and the event id of the "placed" transition.
    @Override
    @Transactional
    public OrderTransitionResult placeOrder(String sessionId, PlaceOrderRequest request) {
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

        return saveAndPublish(order, null);
    }

    // Cancels an order. A customer can cancel only their own order while it is placed;
    // an operator can cancel any order up to ready (the State classes enforce this).
    @Override
    @Transactional
    public OrderTransitionResult cancelOrder(String sessionId, String orderId) {
        Session session = currentSession(sessionId);
        Order order = existingOrder(orderId);

        // Customers only see their own orders; operators may cancel any order
        if (session.getRole() == Role.CUSTOMER && !order.getCustomerId().equals(session.getAccountId())) {
            throw new OrderNotFoundException();
        }

        String previousState = order.getStatus();

        // Leaving "accepted" also removes the order from the preparation queue
        order.cancel(session.getRole());

        return saveAndPublish(order, previousState);
    }

    // Bakery staff accept a placed order of their own bakery.
    @Override
    @Transactional
    public OrderTransitionResult acceptOrder(String sessionId, String orderId) {
        Order order = staffOrder(sessionId, orderId);
        String previousState = order.getStatus();

        // An accepted order joins its bakery's preparation queue
        order.accept();

        return saveAndPublish(order, previousState);
    }

    // Bakery staff reject a placed order of their own bakery; a non-blank reason is required and stored.
    @Override
    @Transactional
    public OrderTransitionResult rejectOrder(String sessionId, String orderId, String reason) {
        Order order = staffOrder(sessionId, orderId);
        String previousState = order.getStatus();

        order.reject(reason);

        return saveAndPublish(order, previousState);
    }

    // Bakery staff start preparing an accepted order; only the first order in their
    // bakery's preparation queue (oldest accepted order) may start.
    @Override
    @Transactional
    public OrderTransitionResult startPreparing(String sessionId, String orderId) {
        Order order = staffOrder(sessionId, orderId);
        String previousState = order.getStatus();

        // Checked before the state changes so a refused order stays untouched
        if (!isFirstInQueue(order)) {
            throw new NotFirstInQueueException();
        }

        order.startPreparing();

        return saveAndPublish(order, previousState);
    }

    // Bakery staff mark an order of their own bakery as ready once it has been prepared.
    @Override
    @Transactional
    public OrderTransitionResult markReady(String sessionId, String orderId) {
        Order order = staffOrder(sessionId, orderId);
        String previousState = order.getStatus();

        order.markReady();

        return saveAndPublish(order, previousState);
    }

    // A courier confirms they picked up their order at the bakery (UC-DLV-05): the order
    // becomes picked_up and the courier carrying. An order cannot be picked up before it is
    // ready (R-ORD-04, enforced by the State classes).
    @Override
    @Transactional
    public OrderTransitionResult confirmPickup(String sessionId, String orderId) {
        Courier courier = courierHolding(sessionId, orderId);
        Order order = existingOrder(orderId);
        String previousState = order.getStatus();

        order.confirmPickup();
        courier.markPickedUp();

        courierRegistry.save(courier);
        return saveAndPublish(order, previousState);
    }

    // A courier confirms they delivered their order to the customer (UC-DLV-06): the order
    // becomes delivered and the courier idle again, so waiting orders are offered again.
    // No state can be skipped: a ready order must be picked up first (R-ORD-04).
    @Override
    @Transactional
    public OrderTransitionResult confirmDelivery(String sessionId, String orderId) {
        Courier courier = courierHolding(sessionId, orderId);
        Order order = existingOrder(orderId);
        String previousState = order.getStatus();

        order.confirmDelivery();
        courier.markDelivered();

        courierRegistry.save(courier);
        OrderTransitionResult result = saveAndPublish(order, previousState);
        retryWaitingOrdersAfterCommit(courier.getCourierId());
        return result;
    }

    // A customer's own orders with their current state, newest first (UC-ORD-12).
    @Override
    @Transactional(readOnly = true)
    public List<OrderView> listOrdersForCustomer(String sessionId) {
        Session session = currentSession(sessionId);
        return views(orderRegistry.findByCustomerIdOrderByCreatedAtDesc(session.getAccountId()).stream());
    }

    // The orders of the staff member's own bakery, oldest first: incoming orders,
    // the preparation queue in arrival order, and orders being prepared or ready.
    @Override
    @Transactional(readOnly = true)
    public List<OrderView> listOrdersForBakery(String sessionId) {
        String bakeryId = bakeryStaffAccessService.getBakeryIdForSession(sessionId);
        return views(orderRegistry.findByBakeryIdOrderByCreatedAtAsc(bakeryId).stream());
    }

    // Every order of every bakery for the operator, newest first, optionally filtered
    // by bakery and by state (UC-ORD-13).
    @Override
    @Transactional(readOnly = true)
    public List<OrderView> listAllOrders(String sessionId, String bakeryId, String status) {
        OrderStatus statusFilter = statusFilter(status);
        return views(orderRegistry.findAll().stream()
                .filter(order -> bakeryId == null || bakeryId.isBlank() || order.getBakeryId().equals(bakeryId))
                .filter(order -> statusFilter == null || order.getStatus().equals(statusFilter.name()))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt())));
    }

    // One order with its lines and the history of its state changes. Customers only see their
    // own orders and staff only their bakery's; anything else is reported as not found.
    @Override
    @Transactional(readOnly = true)
    public OrderView getOrder(String sessionId, String orderId) {
        Session session = currentSession(sessionId);
        Order order = existingOrder(orderId);

        if (session.getRole() == Role.CUSTOMER && !order.getCustomerId().equals(session.getAccountId())) {
            throw new OrderNotFoundException();
        }

        if (session.getRole() == Role.RESTAURANT
                && !order.getBakeryId().equals(bakeryStaffAccessService.getBakeryIdForSession(sessionId))) {
            throw new OrderNotFoundException();
        }

        List<OrderView.Transition> history = eventRepository
                .findByEntityTypeAndEntityIdOrderByTimestampAsc(EntityType.ORDER, orderId).stream()
                .map(event -> new OrderView.Transition(
                        event.getEventId(), event.getPreviousState(), event.getNewState(), event.getTimestamp()))
                .toList();

        return view(order, queuePositions(order.getBakeryId()), history);
    }

    @Override
    public List<Bakery> getOpenBakeries(String sessionId) {
        // Customer-facing calls reach this method through the proxy's access checks.
        return bakeryService.getOpenBakeries();
    }

    // Every bakery with its status, for the operator
    @Override
    public List<Bakery> listAllBakeries(String sessionId) {
        return bakeryService.getAllBakeries();
    }

    @Override
    public List<MenuItem> getMenuForBrowsing(String sessionId, String bakeryId) {
        // MenuService checks bakery availability and preserves sold-out items in the result.
        return menuService.getMenuForBrowsing(bakeryId);
    }

    // The staff member's own bakery menu; the bakery comes from the account, never from the request
    @Override
    public List<MenuItem> listOwnMenuItems(String sessionId) {
        String bakeryId = bakeryStaffAccessService.getBakeryIdForSession(sessionId);
        return menuService.getAllMenuItemsByBakeryId(bakeryId);
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

    // Operator command: reload accounts, bakeries and menus from the config files and clear
    // orders, queues and events. Every session ends, including the operator's.
    @Override
    public InitialStateSummary restoreInitialState(String sessionId) {
        return initialStateService.loadInitialState();
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
            throw new OrderNotFoundException();
        }

        return order;
    }

    // Loads the courier of the logged-in account; a courier only acts on the order bound to
    // them (UC-DLV-05 3a, UC-DLV-06 3a). The courier id is the courier's account id.
    private Courier courierHolding(String sessionId, String orderId) {
        Session session = currentSession(sessionId);
        Courier courier = courierRegistry.findById(session.getAccountId())
                .orElseThrow(() -> new IllegalStateException("No courier found for this account"));

        if (!orderId.equals(courier.getCurrentOrderId())) {
            throw new IllegalStateException("This order is not assigned to you");
        }

        return courier;
    }

    // The courier is idle again, so waiting orders are dispatched (UC-DLV-06 step 7, R-DLV-04).
    // This runs after the commit, so a delivery that rolls back never creates an offer.
    private void retryWaitingOrdersAfterCommit(String courierId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatchService.retryWaitingOrdersWhenCourierBecomesIdle(courierId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatchService.retryWaitingOrdersWhenCourierBecomesIdle(courierId);
            }
        });
    }

    // Loads an order by id, or refuses the command if it does not exist.
    private Order existingOrder(String orderId) {
        return orderRegistry.findById(orderId)
                .orElseThrow(OrderNotFoundException::new);
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
    private OrderTransitionResult saveAndPublish(Order order, String previousState) {
        Order saved = orderRegistry.save(order);
        String eventId = UUID.randomUUID().toString();
        transitionPublisher.notifyObservers(new StateTransitionEvent(
                eventId,
                EntityType.ORDER,
                saved.getId(),
                previousState,
                saved.getStatus(),
                Instant.now()));
        return new OrderTransitionResult(
                saved.getId(), previousState, saved.getStatus(), eventId,
                queuePositions(saved.getBakeryId()).get(saved.getId()));
    }

    // Position (1 = first) of each order in its bakery's preparation queue
    private Map<String, Integer> queuePositions(String bakeryId) {
        Map<String, Integer> positions = new HashMap<>();
        List<Order> queue = preparationQueue.getPreparationQueue(bakeryId);
        for (int i = 0; i < queue.size(); i++) {
            positions.put(queue.get(i).getId(), i + 1);
        }
        return positions;
    }

    private List<OrderView> views(Stream<Order> orders) {
        Map<String, Map<String, Integer>> positionsByBakery = new HashMap<>();
        return orders
                .map(order -> view(order,
                        positionsByBakery.computeIfAbsent(order.getBakeryId(), this::queuePositions),
                        List.of()))
                .toList();
    }

    private OrderView view(Order order, Map<String, Integer> queuePositions, List<OrderView.Transition> history) {
        List<OrderView.Line> lines = order.getOrderItems().stream()
                .map(this::line)
                .toList();

        return new OrderView(
                order.getId(),
                order.getBakeryId(),
                order.getCustomerId(),
                order.getRecipientName(),
                order.getDeliveryAddress(),
                order.getStatus(),
                order.getRejectionReason(),
                lines,
                order.calculateTotal(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                queuePositions.get(order.getId()),
                history);
    }

    private OrderView.Line line(OrderLine line) {
        return new OrderView.Line(
                line.getMenuItem().getId(),
                line.getItemName(),
                line.getUnitPrice(),
                line.getQuantity(),
                line.calculateSubtotal());
    }

    private OrderStatus statusFilter(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return OrderStatus.valueOf(status.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown order state: " + status);
        }
    }

    public int getPreparationQueueSize(String sessionId, String bakeryId) {
        int queueSize = preparationQueue.getPreparationQueueSize(bakeryId);
        return queueSize;
    }
}
