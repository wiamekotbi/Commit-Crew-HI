package com.commitcrew.thebakeway.domain.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.commitcrew.thebakeway.domain.account.Role;

class OrderLifecycleTest {

    private static final Map<String, Consumer<Order>> ACTIONS = new LinkedHashMap<>();

    static {
        ACTIONS.put("accept", Order::accept);
        ACTIONS.put("reject", order -> order.reject("Out of flour"));
        ACTIONS.put("startPreparing", Order::startPreparing);
        ACTIONS.put("markReady", Order::markReady);
        ACTIONS.put("confirmPickup", Order::confirmPickup);
        ACTIONS.put("confirmDelivery", Order::confirmDelivery);
        ACTIONS.put("cancelAsCustomer", order -> order.cancel(Role.CUSTOMER));
        ACTIONS.put("cancelAsOperator", order -> order.cancel(Role.OPERATOR));
    }

    // Every legal transition; any other status and action pair must be refused
    private static final Map<String, String> LEGAL = Map.ofEntries(
            Map.entry("placed:accept", "accepted"),
            Map.entry("placed:reject", "rejected"),
            Map.entry("placed:cancelAsCustomer", "cancelled"),
            Map.entry("placed:cancelAsOperator", "cancelled"),
            Map.entry("accepted:startPreparing", "preparing"),
            Map.entry("accepted:cancelAsOperator", "cancelled"),
            Map.entry("preparing:markReady", "ready"),
            Map.entry("preparing:cancelAsOperator", "cancelled"),
            Map.entry("ready:confirmPickup", "picked_up"),
            Map.entry("ready:cancelAsOperator", "cancelled"),
            Map.entry("picked_up:confirmDelivery", "delivered"));

    @Test
    void followsTheFullLifecycle() {
        Order order = newOrder();

        order.accept();
        assertEquals("accepted", order.getStatus());
        order.startPreparing();
        assertEquals("preparing", order.getStatus());
        order.markReady();
        assertEquals("ready", order.getStatus());
        order.confirmPickup();
        assertEquals("picked_up", order.getStatus());
        order.confirmDelivery();
        assertEquals("delivered", order.getStatus());
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("everyStatusAndAction")
    void allowsOnlyLegalTransitions(OrderStatus status, String action) {
        Order order = orderIn(status);

        String expected = LEGAL.get(status.name() + ":" + action);
        if (expected != null) {
            ACTIONS.get(action).accept(order);
            assertEquals(expected, order.getStatus());
        } else {
            assertThrows(IllegalTransitionException.class, () -> ACTIONS.get(action).accept(order));
            assertEquals(status.name(), order.getStatus());
        }
    }

    @Test
    void restoresStateFromStoredStatus() {
        Order order = newOrder();
        ReflectionTestUtils.setField(order, "status", OrderStatus.ready);
        ReflectionTestUtils.setField(order, "state", null);

        order.restoreState();

        assertEquals("ready", order.getStatus());
        order.confirmPickup();
        assertEquals("picked_up", order.getStatus());
    }

    @Test
    void rejectStoresTheReason() {
        Order order = newOrder();

        order.reject("Out of flour");

        assertEquals("rejected", order.getStatus());
        assertEquals("Out of flour", order.getRejectionReason());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectRequiresAReason(String reason) {
        Order order = newOrder();

        assertThrows(IllegalArgumentException.class, () -> order.reject(reason));

        assertEquals("placed", order.getStatus());
        assertNull(order.getRejectionReason());
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"RESTAURANT", "COURIER"})
    void onlyCustomerOrOperatorCanCancelPlacedOrder(Role role) {
        Order order = newOrder();

        assertThrows(IllegalTransitionException.class, () -> order.cancel(role));

        assertEquals("placed", order.getStatus());
    }

    @Test
    void refusedTransitionNamesStatusAndAction() {
        Order order = newOrder();

        IllegalTransitionException exception =
                assertThrows(IllegalTransitionException.class, order::markReady);

        assertEquals("Cannot mark ready an order that is placed", exception.getMessage());
    }

    static Stream<Arguments> everyStatusAndAction() {
        List<Arguments> arguments = new ArrayList<>();
        for (OrderStatus status : OrderStatus.values()) {
            for (String action : ACTIONS.keySet()) {
                arguments.add(Arguments.of(status, action));
            }
        }
        return arguments.stream();
    }

    private static Order newOrder() {
        return new Order("customer-1", "bakery-1", "Maria", "Montreal");
    }

    private static Order orderIn(OrderStatus status) {
        Order order = newOrder();
        order.setState(OrderStateFactory.fromStatus(status.name()));
        return order;
    }
}
