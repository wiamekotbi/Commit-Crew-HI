package com.commitcrew.thebakeway.domain.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.junit.jupiter.params.provider.MethodSource;
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

    // The only legal transitions so far (reject and cancel come with the reject/cancel issue)
    private static final Map<String, String> LEGAL = Map.of(
            "placed:accept", "accepted",
            "accepted:startPreparing", "preparing",
            "preparing:markReady", "ready",
            "ready:confirmPickup", "picked_up",
            "picked_up:confirmDelivery", "delivered");

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
