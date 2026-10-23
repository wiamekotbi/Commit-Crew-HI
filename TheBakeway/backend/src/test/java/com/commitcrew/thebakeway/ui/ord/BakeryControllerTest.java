package com.commitcrew.thebakeway.ui.ord;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.commitcrew.thebakeway.application.ord.BakeryNotFoundException;
import com.commitcrew.thebakeway.application.ord.BakeryUnavailableException;
import com.commitcrew.thebakeway.application.ord.OrderService;
import com.commitcrew.thebakeway.domain.bakery.Bakery;
import com.commitcrew.thebakeway.domain.bakery.BakeryStatus;
import com.commitcrew.thebakeway.domain.menu.MenuItem;
import com.commitcrew.thebakeway.domain.menu.MenuItemStatus;

@ExtendWith(MockitoExtension.class)
class BakeryControllerTest {

    private static final String SESSION_ID = "session-1";
    private static final String BAKERY_ID = "bakery-1";
    private static final String BAKERIES_URL = "/api/bakeries";
    private static final String MENU_URL = "/api/bakeries/bakery-1/menu";

    @Mock
    private OrderService orderService;

    private BakeryController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        controller = new BakeryController(orderService);
        // Exercise Spring MVC routing, cookie binding, JSON serialization and local
        // exception handlers without starting the application or connecting to Supabase.
        // The service boundary is mocked; proxy authorization has its own tests.
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void returnsOpenBakeriesAndForwardsSessionCookie() throws Exception {
        Bakery bakery = new Bakery(BAKERY_ID, "Sweet Treats", BakeryStatus.open, "123 Main St");
        when(orderService.getOpenBakeries(SESSION_ID)).thenReturn(List.of(bakery));

        mockMvc.perform(get(BAKERIES_URL).cookie(sessionCookie(SESSION_ID)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].bakeryId").value(BAKERY_ID))
                .andExpect(jsonPath("$[0].bakeryName").value("Sweet Treats"))
                .andExpect(jsonPath("$[0].bakeryStatus").value("open"))
                .andExpect(jsonPath("$[0].bakeryAddress").value("123 Main St"));

        verify(orderService).getOpenBakeries(SESSION_ID);
    }

    @Test
    void returnsMenuIncludingSoldOutItemsAndForwardsBakeryId() throws Exception {
        MenuItem available = new MenuItem(BAKERY_ID, "Croissant", "Butter croissant", 3.50,
                MenuItemStatus.available);
        MenuItem soldOut = new MenuItem(BAKERY_ID, "Baguette", "French baguette", 4.00,
                MenuItemStatus.sold_out);
        // Simulate IDs assigned by JPA so the JSON contract includes populated item IDs.
        // This does not test ID generation or require adding production setters.
        ReflectionTestUtils.setField(available, "id", "item-1");
        ReflectionTestUtils.setField(soldOut, "id", "item-2");
        when(orderService.getMenuForBrowsing(SESSION_ID, BAKERY_ID))
                .thenReturn(List.of(available, soldOut));

        mockMvc.perform(get(MENU_URL).cookie(sessionCookie(SESSION_ID)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("item-1"))
                .andExpect(jsonPath("$[0].bakeryId").value(BAKERY_ID))
                .andExpect(jsonPath("$[0].name").value("Croissant"))
                .andExpect(jsonPath("$[0].description").value("Butter croissant"))
                .andExpect(jsonPath("$[0].price").value(3.50))
                .andExpect(jsonPath("$[0].status").value("available"))
                .andExpect(jsonPath("$[1].id").value("item-2"))
                .andExpect(jsonPath("$[1].name").value("Baguette"))
                .andExpect(jsonPath("$[1].price").value(4.00))
                .andExpect(jsonPath("$[1].status").value("sold_out"));

        verify(orderService).getMenuForBrowsing(SESSION_ID, BAKERY_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {BAKERIES_URL, MENU_URL})
    void returnsEmptyResultsAsSuccessfulJsonArrays(String url) throws Exception {
        if (BAKERIES_URL.equals(url)) {
            when(orderService.getOpenBakeries(SESSION_ID)).thenReturn(List.of());
        } else {
            when(orderService.getMenuForBrowsing(SESSION_ID, BAKERY_ID)).thenReturn(List.of());
        }

        mockMvc.perform(get(url).cookie(sessionCookie(SESSION_ID)))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        verifyBrowsingCall(url, SESSION_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {BAKERIES_URL, MENU_URL})
    void mapsMissingCookieToUnauthorized(String url) throws Exception {
        // required=false must forward null to the protected service, not produce HTTP 400.
        stubBrowsingFailure(url, null, new IllegalStateException("Authentication required"));

        mockMvc.perform(get(url))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Authentication required"));
        verifyBrowsingCall(url, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {BAKERIES_URL, MENU_URL})
    void mapsInvalidSessionToUnauthorized(String url) throws Exception {
        stubBrowsingFailure(url, "invalid-session", new IllegalStateException("Authentication required"));

        mockMvc.perform(get(url).cookie(sessionCookie("invalid-session")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Authentication required"));
        verifyBrowsingCall(url, "invalid-session");
    }

    @ParameterizedTest
    @ValueSource(strings = {BAKERIES_URL, MENU_URL})
    void mapsDeniedPermissionToForbidden(String url) throws Exception {
        stubBrowsingFailure(url, SESSION_ID, new SecurityException("Access denied for operation: BROWSE_MENU"));

        mockMvc.perform(get(url).cookie(sessionCookie(SESSION_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access denied for browsing"));
        verifyBrowsingCall(url, SESSION_ID);
    }

    @Test
    void mapsMissingBakeryToNotFound() throws Exception {
        String message = "Bakery not found with ID: " + BAKERY_ID;
        stubBrowsingFailure(MENU_URL, SESSION_ID, new BakeryNotFoundException(message));

        mockMvc.perform(get(MENU_URL).cookie(sessionCookie(SESSION_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(message));
    }

    @ParameterizedTest
    @ValueSource(strings = {"paused", "closed"})
    void mapsUnavailableBakeryToConflict(String bakeryStatus) throws Exception {
        String message = "Bakery " + BAKERY_ID + " is unavailable for browsing: " + bakeryStatus;
        stubBrowsingFailure(MENU_URL, SESSION_ID, new BakeryUnavailableException(message));

        // The specific bakery handler must win over its IllegalStateException parent handler.
        mockMvc.perform(get(MENU_URL).cookie(sessionCookie(SESSION_ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(message));
    }

    @Test
    void doesNotDisguiseUnexpectedStateErrorsAsAuthenticationFailures() {
        IllegalStateException failure = new IllegalStateException("Unexpected application failure");

        // Test the handler's fallback directly: unrelated failures must be rethrown.
        IllegalStateException actual = assertThrows(IllegalStateException.class,
                () -> controller.handleAuthenticationRequired(failure));
        assertSame(failure, actual);
    }

    private Cookie sessionCookie(String sessionId) {
        return new Cookie("BAKEWAY_SESSION_ID", sessionId);
    }

    // Stub only the operation exercised by each parameterized case, avoiding unused stubs.
    private void stubBrowsingFailure(String url, String sessionId, RuntimeException failure) {
        if (BAKERIES_URL.equals(url)) {
            when(orderService.getOpenBakeries(sessionId)).thenThrow(failure);
        } else {
            when(orderService.getMenuForBrowsing(sessionId, BAKERY_ID)).thenThrow(failure);
        }
    }

    private void verifyBrowsingCall(String url, String sessionId) {
        if (BAKERIES_URL.equals(url)) {
            verify(orderService).getOpenBakeries(sessionId);
        } else {
            verify(orderService).getMenuForBrowsing(sessionId, BAKERY_ID);
        }
    }

    @Test
    void returnsAllBakeriesForTheOperator() throws Exception {
        Bakery paused = new Bakery("bakery-2", "Old Mill", BakeryStatus.paused, "8 Mill Road");
        when(orderService.listAllBakeries(SESSION_ID)).thenReturn(List.of(paused));

        mockMvc.perform(get(BAKERIES_URL + "/all").cookie(sessionCookie(SESSION_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bakeryStatus").value("paused"));
    }
}
