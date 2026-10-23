package com.commitcrew.thebakeway.ui.registration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.commitcrew.thebakeway.application.reg.LoginService;
import com.commitcrew.thebakeway.application.reg.ProfileService;
import com.commitcrew.thebakeway.domain.account.BakewayAccount;
import com.commitcrew.thebakeway.domain.account.Role;
import com.commitcrew.thebakeway.domain.session.Session;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    private static final String SESSION_ID = "session-1";

    @Mock
    private ProfileService profileService;

    @Mock
    private LoginService loginService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProfileController(profileService, loginService)).build();
    }

    @Test
    void showsTheLoggedInUsersOwnProfile() throws Exception {
        givenSessionFor("account-1");
        when(profileService.getProfile("account-1")).thenReturn(account());

        mockMvc.perform(get("/api/profiles/account-1").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("maria"));
    }

    @Test
    void requiresASession() throws Exception {
        when(loginService.findSession(null)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/profiles/account-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Authentication required"));

        verify(profileService, never()).getProfile(any());
    }

    @Test
    void refusesAnotherAccountsProfile() throws Exception {
        givenSessionFor("account-2");

        mockMvc.perform(get("/api/profiles/account-1").cookie(sessionCookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("You can only view and edit your own profile"));

        verify(profileService, never()).getProfile(any());
    }

    @Test
    void refusesUpdatingAnotherAccountsProfile() throws Exception {
        givenSessionFor("account-2");

        mockMvc.perform(put("/api/profiles/account-1").cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Hacker\",\"contactEmail\":\"\",\"deliveryAddress\":\"\"}"))
                .andExpect(status().isForbidden());

        verify(profileService, never()).updateProfile(any(), any(), any(), any());
    }

    @Test
    void updatesTheLoggedInUsersOwnProfile() throws Exception {
        givenSessionFor("account-1");
        when(profileService.updateProfile("account-1", "Maria L", "maria@example.com", "Montreal"))
                .thenReturn(account());

        mockMvc.perform(put("/api/profiles/account-1").cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Maria L\",\"contactEmail\":\"maria@example.com\","
                                + "\"deliveryAddress\":\"Montreal\"}"))
                .andExpect(status().isOk());
    }

    private void givenSessionFor(String accountId) {
        when(loginService.findSession(SESSION_ID))
                .thenReturn(Optional.of(new Session(SESSION_ID, accountId, Role.CUSTOMER)));
    }

    private BakewayAccount account() {
        return new BakewayAccount("account-1", "maria", "encoded", "Maria", "", "Montreal", Role.CUSTOMER);
    }

    private Cookie sessionCookie() {
        return new Cookie("BAKEWAY_SESSION_ID", SESSION_ID);
    }
}
