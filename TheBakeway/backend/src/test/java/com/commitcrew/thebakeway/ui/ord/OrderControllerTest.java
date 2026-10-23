package com.commitcrew.thebakeway.ui.ord;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.commitcrew.thebakeway.application.ord.BakeryUnavailableException;
import com.commitcrew.thebakeway.application.ord.NotFirstInQueueException;
import com.commitcrew.thebakeway.application.ord.OrderNotFoundException;
import com.commitcrew.thebakeway.application.ord.OrderService;
import com.commitcrew.thebakeway.application.ord.OrderTransitionResult;
import com.commitcrew.thebakeway.application.ord.OrderView;
import com.commitcrew.thebakeway.application.ord.PlaceOrderRequest;
import com.commitcrew.thebakeway.domain.order.IllegalTransitionException;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    private static final String SESSION_ID = "session-1";
    private static final String ORDER_ID = "order-1";

    @Mock
    private OrderService orderService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OrderController(orderService)).build();
    }

    @Test
    void placesAnOrderAndReturnsTheEventId() throws Exception {
        when(orderService.placeOrder(eq(SESSION_ID), any(PlaceOrderRequest.class)))
                .thenReturn(new OrderTransitionResult(ORDER_ID, null, "placed", "event-1", null));

        mockMvc.perform(post("/api/orders").cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bakeryId":"bakery-1","items":[{"menuItemId":"item-1","quantity":2}],
                                 "deliveryAddress":"Montreal"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(ORDER_ID))
                .andExpect(jsonPath("$.newState").value("placed"))
                .andExpect(jsonPath("$.eventId").value("event-1"));
    }

    @Test
    void acceptReturnsTheQueuePosition() throws Exception {
        when(orderService.acceptOrder(SESSION_ID, ORDER_ID))
                .thenReturn(new OrderTransitionResult(ORDER_ID, "placed", "accepted", "event-2", 3));

        mockMvc.perform(patch("/api/orders/order-1/accept").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newState").value("accepted"))
                .andExpect(jsonPath("$.queuePosition").value(3));
    }

    @Test
    void rejectPassesTheReason() throws Exception {
        when(orderService.rejectOrder(SESSION_ID, ORDER_ID, "Out of flour"))
                .thenReturn(new OrderTransitionResult(ORDER_ID, "placed", "rejected", "event-3", null));

        mockMvc.perform(patch("/api/orders/order-1/reject").cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Out of flour\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newState").value("rejected"));
    }

    @Test
    void forwardsStartPreparingReadyAndCancel() throws Exception {
        OrderTransitionResult result = new OrderTransitionResult(ORDER_ID, "a", "b", "e", null);
        when(orderService.startPreparing(SESSION_ID, ORDER_ID)).thenReturn(result);
        when(orderService.markReady(SESSION_ID, ORDER_ID)).thenReturn(result);
        when(orderService.cancelOrder(SESSION_ID, ORDER_ID)).thenReturn(result);

        mockMvc.perform(patch("/api/orders/order-1/start-preparing").cookie(sessionCookie()))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/orders/order-1/ready").cookie(sessionCookie()))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/orders/order-1/cancel").cookie(sessionCookie()))
                .andExpect(status().isOk());

        verify(orderService).startPreparing(SESSION_ID, ORDER_ID);
        verify(orderService).markReady(SESSION_ID, ORDER_ID);
        verify(orderService).cancelOrder(SESSION_ID, ORDER_ID);
    }

    @Test
    void listsOrdersForEachRole() throws Exception {
        when(orderService.listOrdersForCustomer(SESSION_ID)).thenReturn(List.of(view()));
        when(orderService.listOrdersForBakery(SESSION_ID)).thenReturn(List.of(view()));
        when(orderService.listAllOrders(SESSION_ID, "bakery-1", "placed")).thenReturn(List.of(view()));
        when(orderService.getOrder(SESSION_ID, ORDER_ID)).thenReturn(view());

        mockMvc.perform(get("/api/orders/mine").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(ORDER_ID));
        mockMvc.perform(get("/api/orders/bakery").cookie(sessionCookie()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/orders").param("bakeryId", "bakery-1").param("status", "placed")
                        .cookie(sessionCookie()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/orders/order-1").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("placed"));
    }

    @Test
    void illegalTransitionIsAConflict() throws Exception {
        when(orderService.markReady(SESSION_ID, ORDER_ID))
                .thenThrow(new IllegalTransitionException("placed", "mark ready"));

        mockMvc.perform(patch("/api/orders/order-1/ready").cookie(sessionCookie()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Cannot mark ready an order that is placed"));
    }

    @Test
    void notFirstInQueueIsAConflict() throws Exception {
        when(orderService.startPreparing(SESSION_ID, ORDER_ID)).thenThrow(new NotFirstInQueueException());

        mockMvc.perform(patch("/api/orders/order-1/start-preparing").cookie(sessionCookie()))
                .andExpect(status().isConflict());
    }

    @Test
    void pausedBakeryIsAConflict() throws Exception {
        when(orderService.placeOrder(eq(SESSION_ID), any(PlaceOrderRequest.class)))
                .thenThrow(new BakeryUnavailableException("Bakery bakery-1 is unavailable for ordering: paused"));

        mockMvc.perform(post("/api/orders").cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bakeryId\":\"bakery-1\",\"items\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Bakery bakery-1 is unavailable for ordering: paused"));
    }

    @Test
    void invalidInputIsABadRequest() throws Exception {
        when(orderService.rejectOrder(SESSION_ID, ORDER_ID, " "))
                .thenThrow(new IllegalArgumentException("A rejection reason is required"));

        mockMvc.perform(patch("/api/orders/order-1/reject").cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("A rejection reason is required"));
    }

    @Test
    void unknownOrderIsNotFound() throws Exception {
        when(orderService.getOrder(SESSION_ID, ORDER_ID)).thenThrow(new OrderNotFoundException());

        mockMvc.perform(get("/api/orders/order-1").cookie(sessionCookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Order not found"));
    }

    @Test
    void wrongRoleIsForbidden() throws Exception {
        when(orderService.acceptOrder(SESSION_ID, ORDER_ID)).thenThrow(new SecurityException("Access denied"));

        mockMvc.perform(patch("/api/orders/order-1/accept").cookie(sessionCookie()))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingSessionIsUnauthorized() throws Exception {
        when(orderService.listOrdersForCustomer(null)).thenThrow(new IllegalStateException("Authentication required"));

        mockMvc.perform(get("/api/orders/mine"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Authentication required"));
    }

    private OrderView view() {
        return new OrderView(ORDER_ID, "bakery-1", "customer-1", "Maria", "Montreal", "placed", null,
                List.of(new OrderView.Line("item-1", "Croissant", 3.50, 2, 7.00)), 7.00,
                LocalDateTime.now(), LocalDateTime.now(), null, List.of());
    }

    private Cookie sessionCookie() {
        return new Cookie("BAKEWAY_SESSION_ID", SESSION_ID);
    }
}
