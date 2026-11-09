package com.commitcrew.thebakeway.ui.dlv;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.commitcrew.thebakeway.application.dlv.DispatchReassignmentResult;
import com.commitcrew.thebakeway.application.dlv.OperatorDispatchService;
import com.commitcrew.thebakeway.application.dlv.WaitingOrderView;
import com.commitcrew.thebakeway.application.ord.OrderNotFoundException;

@ExtendWith(MockitoExtension.class)
class OperatorDispatchControllerTest {

    private static final String SESSION_ID = "session-operator";
    private static final String ORDER_ID = "order-1";

    @Mock
    private OperatorDispatchService operatorDispatchService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new OperatorDispatchController(operatorDispatchService))
                .build();
    }

    @Test
    void listsOnlyTheApplicationServicesWaitingOrderView() throws Exception {
        when(operatorDispatchService.getWaitingOrders(SESSION_ID))
                .thenReturn(List.of(new WaitingOrderView(
                        ORDER_ID,
                        "bakery-1",
                        "ready",
                        1,
                        null,
                        false)));

        mockMvc.perform(get("/api/dispatch/waiting-orders")
                        .cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(ORDER_ID))
                .andExpect(jsonPath("$[0].bakeryId").value("bakery-1"))
                .andExpect(jsonPath("$[0].queuePosition").value(1))
                .andExpect(jsonPath("$[0].reassignable").value(false))
                // Sensitive order fields are not components of the view and
                // therefore cannot be serialized by this endpoint.
                .andExpect(jsonPath("$[0].customerId").doesNotExist())
                .andExpect(jsonPath("$[0].deliveryAddress").doesNotExist())
                .andExpect(jsonPath("$[0].items").doesNotExist());

        verify(operatorDispatchService).getWaitingOrders(SESSION_ID);
    }

    @Test
    void forwardsReassignmentAndReportsTheReplacementOffer() throws Exception {
        when(operatorDispatchService.reassignStalledOrder(
                SESSION_ID, ORDER_ID))
                .thenReturn(new DispatchReassignmentResult(
                        ORDER_ID,
                        "courier-old",
                        "courier-new",
                        false));

        mockMvc.perform(patch(
                        "/api/dispatch/waiting-orders/{orderId}/reassign",
                        ORDER_ID)
                        .cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(ORDER_ID))
                .andExpect(jsonPath("$.previousCourierId")
                        .value("courier-old"))
                .andExpect(jsonPath("$.offeredCourierId")
                        .value("courier-new"))
                .andExpect(jsonPath("$.waiting").value(false));
    }

    @Test
    void forwardsNoReplacementAsAWaitingResult() throws Exception {
        when(operatorDispatchService.reassignStalledOrder(
                SESSION_ID, ORDER_ID))
                .thenReturn(new DispatchReassignmentResult(
                        ORDER_ID,
                        "courier-old",
                        null,
                        true));

        mockMvc.perform(patch(
                        "/api/dispatch/waiting-orders/{orderId}/reassign",
                        ORDER_ID)
                        .cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offeredCourierId").isEmpty())
                .andExpect(jsonPath("$.waiting").value(true));
    }

    @Test
    void missingSessionReturnsUnauthorized() throws Exception {
        when(operatorDispatchService.getWaitingOrders(null))
                .thenThrow(new IllegalStateException(
                        "Authentication required"));

        mockMvc.perform(get("/api/dispatch/waiting-orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(
                        "Authentication required"));
    }

    @Test
    void nonOperatorReturnsForbiddenWithoutPolicyDetails() throws Exception {
        when(operatorDispatchService.getWaitingOrders(SESSION_ID))
                .thenThrow(new SecurityException("internal policy detail"));

        mockMvc.perform(get("/api/dispatch/waiting-orders")
                        .cookie(sessionCookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(
                        "Access denied for dispatch management"));
    }

    @Test
    void unknownOrderReturnsNotFound() throws Exception {
        when(operatorDispatchService.reassignStalledOrder(
                SESSION_ID, ORDER_ID))
                .thenThrow(new OrderNotFoundException());

        mockMvc.perform(patch(
                        "/api/dispatch/waiting-orders/{orderId}/reassign",
                        ORDER_ID)
                        .cookie(sessionCookie()))
                .andExpect(status().isNotFound());
    }

    @Test
    void ineligibleOrderReturnsConflict() throws Exception {
        when(operatorDispatchService.reassignStalledOrder(
                SESSION_ID, ORDER_ID))
                .thenThrow(new IllegalStateException(
                        "Order is not eligible for reassignment"));

        mockMvc.perform(patch(
                        "/api/dispatch/waiting-orders/{orderId}/reassign",
                        ORDER_ID)
                        .cookie(sessionCookie()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(
                        "Order is not eligible for reassignment"));
    }

    private Cookie sessionCookie() {
        return new Cookie("BAKEWAY_SESSION_ID", SESSION_ID);
    }
}
