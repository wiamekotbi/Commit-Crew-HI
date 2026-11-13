package com.commitcrew.thebakeway.ui.dlv;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.List;
import java.time.Instant;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.commitcrew.thebakeway.application.authorization.PermissionPolicy;
import com.commitcrew.thebakeway.application.dlv.CourierService;
import com.commitcrew.thebakeway.application.dlv.CourierView;
import com.commitcrew.thebakeway.application.dlv.CourierHistoryService;
import com.commitcrew.thebakeway.application.dlv.CompletedDeliveryView;
import com.commitcrew.thebakeway.application.reg.SessionManager;
import com.commitcrew.thebakeway.domain.account.Role;
import com.commitcrew.thebakeway.domain.courier.CourierStatus;
import com.commitcrew.thebakeway.domain.session.Session;

@ExtendWith(MockitoExtension.class)
class CourierControllerTest {

    private static final String SESSION_ID = "session-1";
    private static final String COURIER_ID = "acc-courier-1";
    private static final String OFFER_ID = "offer-1";

    @Mock
    private CourierService courierService;

    @Mock
    private CourierHistoryService courierHistoryService;

    @Mock
    private SessionManager sessionManager;

    @Mock
    private PermissionPolicy permissionPolicy;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new CourierController(courierService, sessionManager, permissionPolicy, courierHistoryService)).build();
    }

    @Test
    void returnsLoggedInCourierState() throws Exception {
        givenCourierSession();
        when(courierService.getCourier(COURIER_ID))
                .thenReturn(view(CourierStatus.IDLE));

        mockMvc.perform(get("/api/couriers/me").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courierId").value(COURIER_ID))
                .andExpect(jsonPath("$.status").value("IDLE"));
    }

    @Test
    void returnsOnlyAuthenticatedCouriersPastDeliveries() throws Exception {
        givenCourierSession();
        when(courierHistoryService.getPastDeliveries(COURIER_ID))
                .thenReturn(List.of(new CompletedDeliveryView("order-2", "bakery-1", "123 Main Street",
                        "delivered", Instant.parse("2026-11-07T12:00:00Z"))));

        mockMvc.perform(get("/api/couriers/me/past-deliveries").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value("order-2"))
                .andExpect(jsonPath("$[0].deliveredAt").exists())
                .andExpect(jsonPath("$[0].amount").doesNotExist())
                .andExpect(jsonPath("$[0].ledgerEntries").doesNotExist());
    }

    @Test
    void rejectsPastDeliveriesForNonCourier() throws Exception {
        when(sessionManager.findSession(SESSION_ID)).thenReturn(Optional.of(
                new Session(SESSION_ID, "customer-1", Role.CUSTOMER)));

        mockMvc.perform(get("/api/couriers/me/past-deliveries").cookie(sessionCookie()))
                .andExpect(status().isForbidden());
    }

    @Test
    void forwardsDutyCommandsForLoggedInCourier() throws Exception {
        givenCourierSession();
        when(courierService.goOnDuty(COURIER_ID))
                .thenReturn(view(CourierStatus.IDLE));
        when(courierService.goOffDuty(COURIER_ID))
                .thenReturn(view(CourierStatus.OFFLINE));

        mockMvc.perform(patch("/api/couriers/me/on-duty")
                        .cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IDLE"));
        mockMvc.perform(patch("/api/couriers/me/off-duty")
                        .cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OFFLINE"));
    }

    @Test
    void forwardsAcceptAndDeclineForLoggedInCourier() throws Exception {
        givenCourierSession();
        when(courierService.getCourier(COURIER_ID))
                .thenReturn(view(CourierStatus.ASSIGNED))
                .thenReturn(view(CourierStatus.IDLE));

        mockMvc.perform(patch("/api/couriers/offers/offer-1/accept")
                        .cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"));
        mockMvc.perform(patch("/api/couriers/offers/offer-1/decline")
                        .cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IDLE"));

        verify(courierService).acceptOffer(COURIER_ID, OFFER_ID);
        verify(courierService).declineOffer(COURIER_ID, OFFER_ID);
    }

    @Test
    void rejectsMissingSession() throws Exception {
        when(sessionManager.findSession(null)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/couriers/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(
                        "Authentication required"));
    }

    @Test
    void rejectsNonCourierRole() throws Exception {
        when(sessionManager.findSession(SESSION_ID)).thenReturn(Optional.of(
                new Session(SESSION_ID, "customer-1", Role.CUSTOMER)));

        mockMvc.perform(get("/api/couriers/me").cookie(sessionCookie()))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidDutyTransitionReturnsConflict() throws Exception {
        givenCourierSession();
        when(courierService.goOffDuty(COURIER_ID))
                .thenThrow(new IllegalStateException(
                        "A courier carrying an order cannot go offline (R-DLV-03)"));

        mockMvc.perform(patch("/api/couriers/me/off-duty")
                        .cookie(sessionCookie()))
                .andExpect(status().isConflict());
    }

    private void givenCourierSession() {
        when(sessionManager.findSession(SESSION_ID)).thenReturn(Optional.of(
                new Session(SESSION_ID, COURIER_ID, Role.COURIER)));
    }

    private CourierView view(CourierStatus status) {
        return new CourierView(
                COURIER_ID, status, null, null, null, null);
    }

    private Cookie sessionCookie() {
        return new Cookie("BAKEWAY_SESSION_ID", SESSION_ID);
    }
}
