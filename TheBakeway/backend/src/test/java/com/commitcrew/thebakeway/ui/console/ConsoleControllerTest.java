package com.commitcrew.thebakeway.ui.console;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.commitcrew.thebakeway.application.console.CommandConsole;
import com.commitcrew.thebakeway.application.reg.SessionManager;
import com.commitcrew.thebakeway.domain.account.Role;

class ConsoleControllerTest {

    private final CommandConsole console = new CommandConsole();
    private final SessionManager sessionManager = new SessionManager();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ConsoleController(console, sessionManager)).build();
        console.record("customer1", "CUSTOMER", "LOGIN", true, "");
        console.record("customer1", "CUSTOMER", "PLACE_ORDER", false, "HTTP 409: Menu item is unavailable");
    }

    @Test
    void returnsEntriesNewerThanAfter() throws Exception {
        Cookie cookie = new Cookie("BAKEWAY_SESSION_ID",
                sessionManager.createSession("acc-1", Role.CUSTOMER).getSessionId());

        mockMvc.perform(get("/api/console").param("after", "1").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].command").value("PLACE_ORDER"))
                .andExpect(jsonPath("$[0].success").value(false));
    }

    @Test
    void requiresASession() throws Exception {
        mockMvc.perform(get("/api/console"))
                .andExpect(status().isUnauthorized());
    }
}
