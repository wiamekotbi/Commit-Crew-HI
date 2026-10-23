package com.commitcrew.thebakeway.ui.console;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.commitcrew.thebakeway.application.console.CommandConsole;
import com.commitcrew.thebakeway.application.console.ConsoleEntry;
import com.commitcrew.thebakeway.application.reg.SessionManager;
import com.commitcrew.thebakeway.domain.account.AccountRegistry;
import com.commitcrew.thebakeway.domain.account.BakewayAccount;
import com.commitcrew.thebakeway.domain.account.Role;
import com.commitcrew.thebakeway.domain.session.Session;

class CommandConsoleFilterTest {

    private final CommandConsole console = new CommandConsole();
    private final SessionManager sessionManager = new SessionManager();
    private final AccountRegistry accountRegistry = Mockito.mock(AccountRegistry.class);

    private MockMvc mockMvc;
    private Cookie staffCookie;

    // Stands in for the real controllers: the filter only sees HTTP requests and replies
    @RestController
    static class FakeController {
        @PatchMapping("/api/orders/{id}/accept")
        Map<String, Object> accept() {
            return Map.of("orderId", "order-123456789", "previousState", "placed", "newState", "accepted",
                    "eventId", "event-987654321", "queuePosition", 2);
        }

        @PatchMapping("/api/orders/{id}/ready")
        ResponseEntity<Map<String, String>> ready() {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Cannot mark ready an order that is placed"));
        }

        // Like the real LoginController, it reads the body; the filter only sees what was read
        @PostMapping("/api/login")
        Map<String, String> login(@RequestBody Map<String, String> credentials) {
            return Map.of("accountId", "acc-1", "role", "CUSTOMER");
        }

        @GetMapping("/api/console")
        List<String> console() {
            return List.of();
        }
    }

    @BeforeEach
    void setUp() {
        Session session = sessionManager.createSession("acc-staff", Role.RESTAURANT);
        staffCookie = new Cookie("BAKEWAY_SESSION_ID", session.getSessionId());
        Mockito.when(accountRegistry.findById("acc-staff")).thenReturn(Optional.of(new BakewayAccount(
                "acc-staff", "staff1", "encoded", "Staff", "", null, Role.RESTAURANT, "bakery-1")));

        mockMvc = MockMvcBuilders.standaloneSetup(new FakeController())
                .addFilters(new CommandConsoleFilter(console, sessionManager, accountRegistry))
                .build();
    }

    @Test
    void logsASuccessfulCommandWithItsNewStateAndEventId() throws Exception {
        mockMvc.perform(patch("/api/orders/order-123456789/accept").cookie(staffCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newState").value("accepted"));

        ConsoleEntry entry = onlyEntry();
        assertEquals("staff1", entry.user());
        assertEquals("RESTAURANT", entry.role());
        assertEquals("ACCEPT_ORDER", entry.command());
        assertTrue(entry.success());
        assertTrue(entry.detail().contains("placed -> accepted"), entry.detail());
        assertTrue(entry.detail().contains("event event-98"), entry.detail());
        assertTrue(entry.detail().contains("queue position 2"), entry.detail());
    }

    @Test
    void logsAnErrorWithTheServersMessage() throws Exception {
        mockMvc.perform(patch("/api/orders/order-1/ready").cookie(staffCookie))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Cannot mark ready an order that is placed"));

        ConsoleEntry entry = onlyEntry();
        assertEquals("MARK_READY", entry.command());
        assertFalse(entry.success());
        assertEquals("HTTP 409: Cannot mark ready an order that is placed", entry.detail());
    }

    @Test
    void logsLoginWithTheUsernameButNeverThePassword() throws Exception {
        mockMvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"customer1\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk());

        ConsoleEntry entry = onlyEntry();
        assertEquals("customer1", entry.user());
        assertEquals("CUSTOMER", entry.role());
        assertEquals("LOGIN", entry.command());
        assertFalse(entry.detail().contains("secret123"));
    }

    @Test
    void doesNotLogReadingTheConsole() throws Exception {
        mockMvc.perform(get("/api/console").cookie(staffCookie)).andExpect(status().isOk());

        assertTrue(console.entriesAfter(0).isEmpty());
    }

    @Test
    void readsJsonFields() {
        String json = "{\"orderId\":\"o-1\",\"previousState\":null,\"queuePosition\":3,\"error\":\"Say \\\"no\\\"\"}";

        assertEquals(Optional.of("o-1"), CommandConsoleFilter.field(json, "orderId"));
        assertEquals(Optional.empty(), CommandConsoleFilter.field(json, "previousState"));
        assertEquals(Optional.of("3"), CommandConsoleFilter.field(json, "queuePosition"));
        assertEquals(Optional.of("Say \"no\""), CommandConsoleFilter.field(json, "error"));
    }

    private ConsoleEntry onlyEntry() {
        List<ConsoleEntry> entries = console.entriesAfter(0);
        assertEquals(1, entries.size());
        return entries.get(0);
    }
}
