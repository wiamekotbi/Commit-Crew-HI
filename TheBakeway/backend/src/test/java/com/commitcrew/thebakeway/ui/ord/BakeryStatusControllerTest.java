package com.commitcrew.thebakeway.ui.ord;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.commitcrew.thebakeway.application.ord.BakeryNotFoundException;
import com.commitcrew.thebakeway.application.ord.OrderService;
import com.commitcrew.thebakeway.domain.bakery.Bakery;
import com.commitcrew.thebakeway.domain.bakery.BakeryStatus;

@ExtendWith(MockitoExtension.class)
class BakeryStatusControllerTest {

    private static final String SESSION_ID = "session-1";
    private static final String BAKERY_ID = "bakery-1";
    private static final String PAUSE_URL = "/api/bakeries/bakery-1/pause";
    private static final String REOPEN_URL = "/api/bakeries/bakery-1/reopen";

    @Mock
    private OrderService orderService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // Standalone MVC testing covers routing, cookie binding, serialization,
        // and local exception mapping without Spring startup or Supabase.
        mockMvc = MockMvcBuilders
                .standaloneSetup(new BakeryStatusController(orderService))
                .build();
    }

    @Test
    void pausesBakeryAndForwardsSessionAndTarget() throws Exception {
        // Arrange: authorization and transition behavior are mocked here because
        // their real implementations have separate focused tests.
        Bakery paused = bakery(BakeryStatus.paused);
        when(orderService.pauseBakery(SESSION_ID, BAKERY_ID)).thenReturn(paused);

        // Act and assert: MVC binds both the authentication cookie and path target,
        // then serializes the updated bakery returned by the application layer.
        mockMvc.perform(patch(PAUSE_URL).cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bakeryId").value(BAKERY_ID))
                .andExpect(jsonPath("$.bakeryStatus").value("paused"));

        verify(orderService).pauseBakery(SESSION_ID, BAKERY_ID);
    }

    @Test
    void reopensBakeryAndForwardsSessionAndTarget() throws Exception {
        // Only an operator reaches this service call in production. This MVC test
        // verifies the transport contract independently from the proxy role test.
        Bakery open = bakery(BakeryStatus.open);
        when(orderService.reopenBakery(SESSION_ID, BAKERY_ID)).thenReturn(open);

        mockMvc.perform(patch(REOPEN_URL).cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bakeryStatus").value("open"));

        verify(orderService).reopenBakery(SESSION_ID, BAKERY_ID);
    }

    @Test
    void mapsMissingSessionToUnauthorized() throws Exception {
        // Cookie binding uses required=false so the protected service receives null
        // and produces the application's standard authentication failure.
        when(orderService.pauseBakery(null, BAKERY_ID))
                .thenThrow(new IllegalStateException("Authentication required"));

        mockMvc.perform(patch(PAUSE_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Authentication required"));
    }

    @Test
    void mapsUnauthorizedBakeryTargetToForbidden() throws Exception {
        // This represents restaurant staff selecting a bakery other than the one
        // stored on their account. The controller must expose no ownership details.
        when(orderService.pauseBakery(SESSION_ID, BAKERY_ID))
                .thenThrow(new SecurityException(
                        "Restaurant staff may pause only their assigned bakery"));

        mockMvc.perform(patch(PAUSE_URL).cookie(sessionCookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(
                        "Access denied for bakery status management"));
    }

    @Test
    void mapsMissingBakeryToNotFound() throws Exception {
        // Authorization may succeed even though the selected bakery was removed
        // or never existed, which is a resource lookup failure rather than denial.
        String message = "Bakery not found with ID: " + BAKERY_ID;
        when(orderService.pauseBakery(SESSION_ID, BAKERY_ID))
                .thenThrow(new BakeryNotFoundException(message));

        mockMvc.perform(patch(PAUSE_URL).cookie(sessionCookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(message));
    }

    @Test
    void mapsClosedBakeryReopenToConflict() throws Exception {
        // The request is authenticated and authorized, but conflicts with the
        // report rule that a closed bakery cannot reopen through this use case.
        String message = "Only a paused bakery can be reopened";
        when(orderService.reopenBakery(SESSION_ID, BAKERY_ID))
                .thenThrow(new IllegalStateException(message));

        mockMvc.perform(patch(REOPEN_URL).cookie(sessionCookie()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(message));
    }

    private Cookie sessionCookie() {
        // Use the exact cookie name issued by LoginController.
        return new Cookie("BAKEWAY_SESSION_ID", SESSION_ID);
    }

    private Bakery bakery(BakeryStatus status) {
        // A helper keeps each test focused on HTTP behavior rather than fixtures.
        return new Bakery(BAKERY_ID, "Sweet Treats", status, "123 Main St");
    }
}
