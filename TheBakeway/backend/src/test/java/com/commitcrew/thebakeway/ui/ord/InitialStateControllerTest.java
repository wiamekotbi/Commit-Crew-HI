package com.commitcrew.thebakeway.ui.ord;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.commitcrew.thebakeway.application.ord.InitialStateSummary;
import com.commitcrew.thebakeway.application.ord.InvalidConfigurationException;
import com.commitcrew.thebakeway.application.ord.OrderService;
import com.commitcrew.thebakeway.domain.account.Role;

@ExtendWith(MockitoExtension.class)
class InitialStateControllerTest {

    private static final String SESSION_ID = "session-1";
    private static final String RESTORE_URL = "/api/initial-state/restore";

    @Mock
    private OrderService orderService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InitialStateController(orderService)).build();
    }

    @Test
    void returnsWhatWasLoaded() throws Exception {
        when(orderService.restoreInitialState(SESSION_ID)).thenReturn(
                new InitialStateSummary(4, 9, Map.of(Role.CUSTOMER, 2, Role.OPERATOR, 1)));

        mockMvc.perform(post(RESTORE_URL).cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bakeries").value(4))
                .andExpect(jsonPath("$.menuItems").value(9))
                .andExpect(jsonPath("$.accountsPerRole.CUSTOMER").value(2));
    }

    @Test
    void refusesUsersWhoAreNotTheOperator() throws Exception {
        when(orderService.restoreInitialState(SESSION_ID)).thenThrow(new SecurityException("Access denied"));

        mockMvc.perform(post(RESTORE_URL).cookie(sessionCookie()))
                .andExpect(status().isForbidden());
    }

    @Test
    void requiresASession() throws Exception {
        when(orderService.restoreInitialState(null)).thenThrow(new IllegalStateException("Authentication required"));

        mockMvc.perform(post(RESTORE_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Authentication required"));
    }

    @Test
    void reportsAnInvalidConfigFile() throws Exception {
        when(orderService.restoreInitialState(SESSION_ID)).thenThrow(
                new InvalidConfigurationException("Invalid city config entry at line 3: price cannot be negative"));

        mockMvc.perform(post(RESTORE_URL).cookie(sessionCookie()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid city config entry at line 3: price cannot be negative"));
    }

    private Cookie sessionCookie() {
        return new Cookie("BAKEWAY_SESSION_ID", SESSION_ID);
    }
}
