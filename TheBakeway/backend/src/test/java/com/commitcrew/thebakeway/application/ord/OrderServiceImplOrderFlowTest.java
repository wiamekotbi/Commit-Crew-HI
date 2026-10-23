package com.commitcrew.thebakeway.application.ord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.commitcrew.thebakeway.application.reg.SessionManager;
import com.commitcrew.thebakeway.domain.account.AccountRegistry;
import com.commitcrew.thebakeway.domain.account.BakewayAccount;
import com.commitcrew.thebakeway.domain.account.Role;
import com.commitcrew.thebakeway.domain.event.EntityType;
import com.commitcrew.thebakeway.domain.event.EventRepository;
import com.commitcrew.thebakeway.domain.event.StateTransitionEvent;
import com.commitcrew.thebakeway.domain.menu.MenuItem;
import com.commitcrew.thebakeway.domain.menu.MenuItemStatus;
import com.commitcrew.thebakeway.domain.order.IllegalTransitionException;
import com.commitcrew.thebakeway.domain.order.Order;
import com.commitcrew.thebakeway.domain.order.OrderLine;
import com.commitcrew.thebakeway.domain.order.OrderRegistry;
import com.commitcrew.thebakeway.domain.session.Session;

// Order placement and lifecycle commands of OrderServiceImpl
@ExtendWith(MockitoExtension.class)
class OrderServiceImplOrderFlowTest {

    private static final String SESSION_ID = "session-1";
    private static final String CUSTOMER_ID = "customer-1";
    private static final String BAKERY_ID = "bakery-1";
    private static final String ORDER_ID = "order-1";

    @Mock
    private BakeryService bakeryService;

    @Mock
    private MenuService menuService;

    @Mock
    private BakeryStaffAccessService bakeryStaffAccessService;

    @Mock
    private BakeryStatusAccessService bakeryStatusAccessService;

    @Mock
    private OrderRegistry orderRegistry;

    @Mock
    private AccountRegistry accountRegistry;

    @Mock
    private SessionManager sessionManager;

    @Mock
    private PreparationQueue preparationQueue;

    @Mock
    private TransitionPublisher transitionPublisher;

    @Mock
    private InitialStateService initialStateService;

    @Mock
    private EventRepository eventRepository;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(
                bakeryService,
                menuService,
                bakeryStaffAccessService,
                bakeryStatusAccessService,
                orderRegistry,
                accountRegistry,
                sessionManager,
                preparationQueue,
                transitionPublisher,
                initialStateService,
                eventRepository);
    }

    // Placing an order

    @Test
    void placesOrderWithCopiedLines() {
        givenCustomer("Old Address");
        givenMenuItem("item-1", BAKERY_ID, "Croissant", 3.00, MenuItemStatus.available, false);
        givenMenuItem("item-2", BAKERY_ID, "Baguette", 4.50, MenuItemStatus.available, false);
        givenSaveAssignsId();

        OrderTransitionResult result = orderService.placeOrder(SESSION_ID, request("Montreal",
                new OrderItemRequest("item-1", 2),
                new OrderItemRequest("item-2", 1)));

        Order saved = savedOrder();
        assertEquals(ORDER_ID, result.orderId());
        assertEquals("placed", result.newState());
        assertEquals("placed", saved.getStatus());
        assertEquals(CUSTOMER_ID, saved.getCustomerId());
        assertEquals(BAKERY_ID, saved.getBakeryId());
        assertEquals("Maria", saved.getRecipientName());
        assertEquals("Montreal", saved.getDeliveryAddress());
        assertNull(saved.getRejectionReason());
        verify(bakeryService).requireOpenForOrdering(BAKERY_ID);

        List<OrderLine> lines = saved.getOrderItems();
        assertEquals(2, lines.size());
        assertLine(lines.get(0), "item-1", "Croissant", 3.00, 2);
        assertLine(lines.get(1), "item-2", "Baguette", 4.50, 1);
        assertEquals(10.50, saved.calculateTotal(), 0.001);
    }

    @Test
    void usesProfileAddressWhenRequestHasNone() {
        givenCustomer("Profile Address");
        givenMenuItem("item-1", BAKERY_ID, "Croissant", 3.00, MenuItemStatus.available, false);
        givenSaveAssignsId();

        orderService.placeOrder(SESSION_ID, request(" ", new OrderItemRequest("item-1", 1)));

        assertEquals("Profile Address", savedOrder().getDeliveryAddress());
    }

    @Test
    void publishesPlacedEventExactlyOnce() {
        givenCustomer("Montreal");
        givenMenuItem("item-1", BAKERY_ID, "Croissant", 3.00, MenuItemStatus.available, false);
        givenSaveAssignsId();

        orderService.placeOrder(SESSION_ID, request("Montreal", new OrderItemRequest("item-1", 1)));

        StateTransitionEvent event = assertTransitionPublished(null, "placed");
        assertNotNull(UUID.fromString(event.getEventId()));
        assertNotNull(event.getTimestamp());
    }

    @Test
    void refusesMissingSession() {
        when(sessionManager.findSession(SESSION_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> orderService.placeOrder(
                SESSION_ID, request("Montreal", new OrderItemRequest("item-1", 1))));

        assertNothingSavedOrPublished();
    }

    @Test
    void refusesBakeryThatIsNotOpen() {
        givenCustomer("Montreal");
        when(bakeryService.requireOpenForOrdering(BAKERY_ID))
                .thenThrow(new BakeryUnavailableException("Bakery bakery-1 is unavailable for ordering: paused"));

        assertPlacementRefused(IllegalStateException.class,
                request("Montreal", new OrderItemRequest("item-1", 1)));
    }

    @Test
    void refusesUnknownBakery() {
        givenCustomer("Montreal");
        when(bakeryService.requireOpenForOrdering(BAKERY_ID))
                .thenThrow(new BakeryNotFoundException("Bakery not found with ID: bakery-1"));

        assertPlacementRefused(IllegalArgumentException.class,
                request("Montreal", new OrderItemRequest("item-1", 1)));
    }

    @Test
    void refusesSoldOutItem() {
        givenCustomer("Montreal");
        givenMenuItem("item-1", BAKERY_ID, "Croissant", 3.00, MenuItemStatus.sold_out, false);

        assertPlacementRefused(IllegalStateException.class,
                request("Montreal", new OrderItemRequest("item-1", 1)));
    }

    @Test
    void refusesDiscontinuedItem() {
        givenCustomer("Montreal");
        givenMenuItem("item-1", BAKERY_ID, "Croissant", 3.00, MenuItemStatus.available, true);

        assertPlacementRefused(IllegalArgumentException.class,
                request("Montreal", new OrderItemRequest("item-1", 1)));
    }

    @Test
    void refusesItemOfAnotherBakery() {
        givenCustomer("Montreal");
        givenMenuItem("item-1", "bakery-2", "Croissant", 3.00, MenuItemStatus.available, false);

        assertPlacementRefused(IllegalArgumentException.class,
                request("Montreal", new OrderItemRequest("item-1", 1)));
    }

    @Test
    void refusesUnknownItem() {
        givenCustomer("Montreal");
        when(menuService.getMenuItem("item-1")).thenThrow(new IllegalArgumentException("Menu item not found"));

        assertPlacementRefused(IllegalArgumentException.class,
                request("Montreal", new OrderItemRequest("item-1", 1)));
    }

    @Test
    void refusesWhenOneOfSeveralItemsIsSoldOut() {
        givenCustomer("Montreal");
        givenMenuItem("item-1", BAKERY_ID, "Croissant", 3.00, MenuItemStatus.available, false);
        givenMenuItem("item-2", BAKERY_ID, "Baguette", 4.50, MenuItemStatus.sold_out, false);

        assertPlacementRefused(IllegalStateException.class, request("Montreal",
                new OrderItemRequest("item-1", 1),
                new OrderItemRequest("item-2", 1)));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void refusesQuantityThatIsNotPositive(int quantity) {
        givenCustomer("Montreal");

        assertPlacementRefused(IllegalArgumentException.class,
                request("Montreal", new OrderItemRequest("item-1", quantity)));
    }

    @Test
    void refusesEmptyItems() {
        givenCustomer("Montreal");

        assertPlacementRefused(IllegalArgumentException.class, request("Montreal"));
    }

    @Test
    void refusesDuplicateItems() {
        givenCustomer("Montreal");

        assertPlacementRefused(IllegalArgumentException.class, request("Montreal",
                new OrderItemRequest("item-1", 1),
                new OrderItemRequest("item-1", 2)));
    }

    // Staff commands

    @Test
    void acceptsPlacedOrder() {
        givenStaffOf(BAKERY_ID);
        Order order = givenStoredOrder();
        givenSaveReturnsOrder();

        orderService.acceptOrder(SESSION_ID, ORDER_ID);

        assertEquals("accepted", order.getStatus());
        assertTransitionPublished("placed", "accepted");
    }

    @Test
    void refusesAcceptingAnOrderTwice() {
        givenStaffOf(BAKERY_ID);
        Order order = givenStoredOrder();
        order.accept();

        assertThrows(IllegalTransitionException.class, () -> orderService.acceptOrder(SESSION_ID, ORDER_ID));

        assertEquals("accepted", order.getStatus());
        assertNothingSavedOrPublished();
    }

    @Test
    void staffCannotActOnAnotherBakerysOrder() {
        givenStaffOf("bakery-2");
        Order order = givenStoredOrder();

        assertThrows(IllegalArgumentException.class, () -> orderService.acceptOrder(SESSION_ID, ORDER_ID));

        assertEquals("placed", order.getStatus());
        assertNothingSavedOrPublished();
    }

    @Test
    void rejectsPlacedOrderWithReason() {
        givenStaffOf(BAKERY_ID);
        Order order = givenStoredOrder();
        givenSaveReturnsOrder();

        orderService.rejectOrder(SESSION_ID, ORDER_ID, "Out of flour");

        assertEquals("rejected", order.getStatus());
        assertEquals("Out of flour", order.getRejectionReason());
        assertTransitionPublished("placed", "rejected");
    }

    @Test
    void refusesRejectWithoutReason() {
        givenStaffOf(BAKERY_ID);
        Order order = givenStoredOrder();

        assertThrows(IllegalArgumentException.class, () -> orderService.rejectOrder(SESSION_ID, ORDER_ID, " "));

        assertEquals("placed", order.getStatus());
        assertNothingSavedOrPublished();
    }

    @Test
    void startsPreparingFirstOrderInQueue() {
        givenStaffOf(BAKERY_ID);
        Order order = givenStoredOrder();
        order.accept();
        when(preparationQueue.getPreparationQueue(BAKERY_ID)).thenReturn(List.of(order));
        givenSaveReturnsOrder();

        orderService.startPreparing(SESSION_ID, ORDER_ID);

        assertEquals("preparing", order.getStatus());
        assertTransitionPublished("accepted", "preparing");
    }

    @Test
    void refusesStartPreparingWhenNotFirstInQueue() {
        givenStaffOf(BAKERY_ID);
        Order order = givenStoredOrder();
        order.accept();
        Order earlier = new Order("customer-2", BAKERY_ID, "Ali", "Laval");
        ReflectionTestUtils.setField(earlier, "id", "order-0");
        when(preparationQueue.getPreparationQueue(BAKERY_ID)).thenReturn(List.of(earlier, order));

        assertThrows(NotFirstInQueueException.class, () -> orderService.startPreparing(SESSION_ID, ORDER_ID));

        assertEquals("accepted", order.getStatus());
        assertNothingSavedOrPublished();
    }

    @Test
    void refusesStartPreparingOrderThatIsNotQueued() {
        givenStaffOf(BAKERY_ID);
        Order order = givenStoredOrder();
        when(preparationQueue.getPreparationQueue(BAKERY_ID)).thenReturn(List.of());

        assertThrows(NotFirstInQueueException.class, () -> orderService.startPreparing(SESSION_ID, ORDER_ID));

        assertEquals("placed", order.getStatus());
        assertNothingSavedOrPublished();
    }

    @Test
    void marksPreparingOrderReady() {
        givenStaffOf(BAKERY_ID);
        Order order = givenStoredOrder();
        order.accept();
        order.startPreparing();
        givenSaveReturnsOrder();

        orderService.markReady(SESSION_ID, ORDER_ID);

        assertEquals("ready", order.getStatus());
        assertTransitionPublished("preparing", "ready");
    }

    @Test
    void refusesMarkReadyBeforePreparing() {
        givenStaffOf(BAKERY_ID);
        Order order = givenStoredOrder();
        order.accept();

        assertThrows(IllegalTransitionException.class, () -> orderService.markReady(SESSION_ID, ORDER_ID));

        assertEquals("accepted", order.getStatus());
        assertNothingSavedOrPublished();
    }

    @Test
    void refusesTransitionOnUnknownOrder() {
        givenStaffOf(BAKERY_ID);
        when(orderRegistry.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> orderService.markReady(SESSION_ID, ORDER_ID));

        assertNothingSavedOrPublished();
    }

    // Command results (new state, event id, queue position)

    @Test
    void acceptReturnsTheEventIdAndTheQueuePosition() {
        givenStaffOf(BAKERY_ID);
        Order order = givenStoredOrder();
        givenSaveReturnsOrder();
        Order earlier = storedOrder("order-0");
        when(preparationQueue.getPreparationQueue(BAKERY_ID)).thenReturn(List.of(earlier, order));

        OrderTransitionResult result = orderService.acceptOrder(SESSION_ID, ORDER_ID);

        StateTransitionEvent event = assertTransitionPublished("placed", "accepted");
        assertEquals(ORDER_ID, result.orderId());
        assertEquals("placed", result.previousState());
        assertEquals("accepted", result.newState());
        assertEquals(event.getEventId(), result.eventId());
        assertEquals(2, result.queuePosition());
    }

    // Reading orders

    @Test
    void customerSeesOnlyTheirOwnOrders() {
        givenSession(CUSTOMER_ID, Role.CUSTOMER);
        Order order = storedOrder(ORDER_ID);
        when(orderRegistry.findByCustomerIdOrderByCreatedAtDesc(CUSTOMER_ID)).thenReturn(List.of(order));

        List<OrderView> orders = orderService.listOrdersForCustomer(SESSION_ID);

        assertEquals(1, orders.size());
        assertEquals(ORDER_ID, orders.get(0).orderId());
        assertEquals("placed", orders.get(0).status());
    }

    @Test
    void staffSeeTheirBakeryOrdersWithQueuePositions() {
        givenStaffOf(BAKERY_ID);
        Order first = storedOrder("order-1");
        first.accept();
        Order second = storedOrder("order-2");
        second.accept();
        Order incoming = storedOrder("order-3");
        when(orderRegistry.findByBakeryIdOrderByCreatedAtAsc(BAKERY_ID)).thenReturn(List.of(first, second, incoming));
        when(preparationQueue.getPreparationQueue(BAKERY_ID)).thenReturn(List.of(first, second));

        List<OrderView> orders = orderService.listOrdersForBakery(SESSION_ID);

        assertEquals(1, orders.get(0).queuePosition());
        assertEquals(2, orders.get(1).queuePosition());
        assertNull(orders.get(2).queuePosition());
    }

    @Test
    void operatorFiltersAllOrdersByBakeryAndState() {
        Order placed = storedOrder("order-1");
        Order accepted = storedOrder("order-2");
        accepted.accept();
        Order otherBakery = new Order(CUSTOMER_ID, "bakery-2", "Maria", "Montreal");
        ReflectionTestUtils.setField(otherBakery, "id", "order-3");
        when(orderRegistry.findAll()).thenReturn(List.of(placed, accepted, otherBakery));

        List<OrderView> orders = orderService.listAllOrders(SESSION_ID, BAKERY_ID, "accepted");

        assertEquals(1, orders.size());
        assertEquals("order-2", orders.get(0).orderId());
    }

    @Test
    void operatorFilterRefusesUnknownState() {
        assertThrows(IllegalArgumentException.class,
                () -> orderService.listAllOrders(SESSION_ID, null, "lost"));
    }

    @Test
    void orderDetailsIncludeTheHistoryOfChanges() {
        givenSession(CUSTOMER_ID, Role.CUSTOMER);
        Order order = givenStoredOrder();
        when(eventRepository.findByEntityTypeAndEntityIdOrderByTimestampAsc(EntityType.ORDER, ORDER_ID))
                .thenReturn(List.of(new StateTransitionEvent(
                        "event-1", EntityType.ORDER, ORDER_ID, null, "placed", java.time.Instant.now())));

        OrderView view = orderService.getOrder(SESSION_ID, ORDER_ID);

        assertEquals(order.getId(), view.orderId());
        assertEquals(1, view.history().size());
        assertEquals("event-1", view.history().get(0).eventId());
        assertEquals("placed", view.history().get(0).newState());
    }

    @Test
    void customerCannotOpenAnotherCustomersOrder() {
        givenSession("customer-2", Role.CUSTOMER);
        givenStoredOrder();

        assertThrows(OrderNotFoundException.class, () -> orderService.getOrder(SESSION_ID, ORDER_ID));
    }

    @Test
    void staffCannotOpenAnotherBakerysOrder() {
        givenSession("staff-2", Role.RESTAURANT);
        givenStaffOf("bakery-2");
        givenStoredOrder();

        assertThrows(OrderNotFoundException.class, () -> orderService.getOrder(SESSION_ID, ORDER_ID));
    }

    // Cancelling

    @Test
    void customerCancelsOwnPlacedOrder() {
        givenSession(CUSTOMER_ID, Role.CUSTOMER);
        Order order = givenStoredOrder();
        givenSaveReturnsOrder();

        orderService.cancelOrder(SESSION_ID, ORDER_ID);

        assertEquals("cancelled", order.getStatus());
        assertTransitionPublished("placed", "cancelled");
    }

    @Test
    void customerCannotCancelAnotherCustomersOrder() {
        givenSession("customer-2", Role.CUSTOMER);
        Order order = givenStoredOrder();

        assertThrows(IllegalArgumentException.class, () -> orderService.cancelOrder(SESSION_ID, ORDER_ID));

        assertEquals("placed", order.getStatus());
        assertNothingSavedOrPublished();
    }

    @Test
    void customerCannotCancelAcceptedOrder() {
        givenSession(CUSTOMER_ID, Role.CUSTOMER);
        Order order = givenStoredOrder();
        order.accept();

        assertThrows(IllegalTransitionException.class, () -> orderService.cancelOrder(SESSION_ID, ORDER_ID));

        assertEquals("accepted", order.getStatus());
        assertNothingSavedOrPublished();
    }

    @Test
    void operatorCancelsAcceptedOrder() {
        givenSession("operator-1", Role.OPERATOR);
        Order order = givenStoredOrder();
        order.accept();
        givenSaveReturnsOrder();

        orderService.cancelOrder(SESSION_ID, ORDER_ID);

        assertEquals("cancelled", order.getStatus());
        assertTransitionPublished("accepted", "cancelled");
    }

    @Test
    void operatorCannotCancelPickedUpOrder() {
        givenSession("operator-1", Role.OPERATOR);
        Order order = givenStoredOrder();
        order.accept();
        order.startPreparing();
        order.markReady();
        order.confirmPickup();

        assertThrows(IllegalTransitionException.class, () -> orderService.cancelOrder(SESSION_ID, ORDER_ID));

        assertEquals("picked_up", order.getStatus());
        assertNothingSavedOrPublished();
    }

    private void givenSession(String accountId, Role role) {
        when(sessionManager.findSession(SESSION_ID)).thenReturn(Optional.of(new Session(SESSION_ID, accountId, role)));
    }

    private void givenCustomer(String profileAddress) {
        givenSession(CUSTOMER_ID, Role.CUSTOMER);
        when(accountRegistry.findById(CUSTOMER_ID)).thenReturn(Optional.of(new BakewayAccount(
                CUSTOMER_ID, "maria", "encoded-password", "Maria", "", profileAddress, Role.CUSTOMER)));
    }

    private void givenStaffOf(String bakeryId) {
        when(bakeryStaffAccessService.getBakeryIdForSession(SESSION_ID)).thenReturn(bakeryId);
    }

    private void givenMenuItem(
            String id, String bakeryId, String name, double price, MenuItemStatus status, boolean discontinued) {
        MenuItem item = new MenuItem(bakeryId, name, "", price, status);
        ReflectionTestUtils.setField(item, "id", id);
        item.setDiscontinued(discontinued);
        when(menuService.getMenuItem(id)).thenReturn(item);
    }

    private Order storedOrder(String id) {
        Order order = new Order(CUSTOMER_ID, BAKERY_ID, "Maria", "Montreal");
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }

    private Order givenStoredOrder() {
        Order order = new Order(CUSTOMER_ID, BAKERY_ID, "Maria", "Montreal");
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        when(orderRegistry.findById(ORDER_ID)).thenReturn(Optional.of(order));
        return order;
    }

    private void givenSaveAssignsId() {
        when(orderRegistry.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "id", ORDER_ID);
            return order;
        });
    }

    private void givenSaveReturnsOrder() {
        when(orderRegistry.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private PlaceOrderRequest request(String deliveryAddress, OrderItemRequest... items) {
        return new PlaceOrderRequest(BAKERY_ID, List.of(items), deliveryAddress);
    }

    private Order savedOrder() {
        ArgumentCaptor<Order> order = ArgumentCaptor.forClass(Order.class);
        verify(orderRegistry).save(order.capture());
        return order.getValue();
    }

    private StateTransitionEvent assertTransitionPublished(String previousState, String newState) {
        ArgumentCaptor<StateTransitionEvent> event = ArgumentCaptor.forClass(StateTransitionEvent.class);
        verify(transitionPublisher, times(1)).notifyObservers(event.capture());
        assertEquals(EntityType.ORDER, event.getValue().getEntityType());
        assertEquals(ORDER_ID, event.getValue().getEntityId());
        assertEquals(previousState, event.getValue().getPreviousState());
        assertEquals(newState, event.getValue().getNewState());
        return event.getValue();
    }

    private void assertLine(OrderLine line, String menuItemId, String name, double unitPrice, int quantity) {
        assertEquals(menuItemId, line.getMenuItem().getId());
        assertEquals(name, line.getItemName());
        assertEquals(unitPrice, line.getUnitPrice(), 0.001);
        assertEquals(quantity, line.getQuantity());
    }

    private void assertPlacementRefused(Class<? extends RuntimeException> expected, PlaceOrderRequest request) {
        assertThrows(expected, () -> orderService.placeOrder(SESSION_ID, request));
        assertNothingSavedOrPublished();
    }

    private void assertNothingSavedOrPublished() {
        verify(orderRegistry, never()).save(any());
        verify(transitionPublisher, never()).notifyObservers(any());
    }
}
