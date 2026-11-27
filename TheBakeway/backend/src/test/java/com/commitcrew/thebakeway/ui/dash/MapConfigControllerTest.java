package com.commitcrew.thebakeway.ui.dash;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.commitcrew.thebakeway.application.reg.SessionManager;
import com.commitcrew.thebakeway.domain.account.Role;

class MapConfigControllerTest {

    private final SessionManager sessionManager = new SessionManager();

    @Test
    void givesTheConfiguredKeyToALoggedInUser() throws Exception {
        MockMvc mvc = mvc(" browser-key ");
        Cookie session = new Cookie("BAKEWAY_SESSION_ID",
                sessionManager.createSession("customer-1", Role.CUSTOMER).getSessionId());

        mvc.perform(get("/api/map/config").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKey").value("browser-key"));
    }

    @Test
    void anUnsetKeyIsEmptySoTheMapShowsAMessage() throws Exception {
        MockMvc mvc = mvc("");
        Cookie session = new Cookie("BAKEWAY_SESSION_ID",
                sessionManager.createSession("operator-1", Role.OPERATOR).getSessionId());

        mvc.perform(get("/api/map/config").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKey").value(""));
    }

    @Test
    void theKeyIsNotGivenWithoutASession() throws Exception {
        mvc("browser-key").perform(get("/api/map/config"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.apiKey").doesNotExist());
    }

    private MockMvc mvc(String apiKey) {
        return MockMvcBuilders.standaloneSetup(new MapConfigController(sessionManager, apiKey)).build();
    }
}
