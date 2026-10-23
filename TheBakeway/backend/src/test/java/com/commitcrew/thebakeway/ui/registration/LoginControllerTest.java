package com.commitcrew.thebakeway.ui.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.commitcrew.thebakeway.application.reg.LoginService;
import com.commitcrew.thebakeway.domain.account.BakewayAccount;
import com.commitcrew.thebakeway.domain.account.Role;
import com.commitcrew.thebakeway.domain.session.Session;

@ExtendWith(MockitoExtension.class)
class LoginControllerTest {

    @Mock
    private LoginService loginService;

    private LoginController loginController;

    @BeforeEach
    void setUp() {
        loginController = new LoginController(loginService);
    }

    @Test
    void loginSetsHttpOnlySessionCookie() {
        Session session = session();
        when(loginService.login("maria", "password")).thenReturn(session);

        ResponseEntity<?> response = loginController.login(
                new LoginRequest("maria", "password"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(new SessionResponse("account-1", Role.CUSTOMER, null, null, null), response.getBody());
        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(cookie);
        assertFalse(cookie.isBlank());
        assertTrue(cookie.contains("BAKEWAY_SESSION_ID=session-1"));
        assertTrue(cookie.contains("HttpOnly"));
    }

    @Test
    void failedLoginDoesNotSetCookie() {
        when(loginService.login("maria", "wrong"))
                .thenThrow(new IllegalArgumentException("Invalid username or password"));

        ResponseEntity<?> response = loginController.login(new LoginRequest("maria", "wrong"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNull(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
    }

    @Test
    void looksUpCurrentSession() {
        when(loginService.findSession("session-1")).thenReturn(Optional.of(session()));

        ResponseEntity<?> response = loginController.getSession("session-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(new SessionResponse("account-1", Role.CUSTOMER, null, null, null), response.getBody());
    }

    @Test
    void rejectsMissingSession() {
        when(loginService.findSession(null)).thenReturn(Optional.empty());

        ResponseEntity<?> response = loginController.getSession(null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void logoutInvalidatesSessionAndExpiresCookie() {
        ResponseEntity<?> response = loginController.logout("session-1");

        verify(loginService).logout("session-1");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(cookie);
        assertTrue(cookie.contains("Max-Age=0"));
    }

    private Session session() {
        return new Session("session-1", "account-1", Role.CUSTOMER);
    }

    @Test
    void sessionIncludesNameAndBakery() {
        Session staffSession = new Session("session-2", "staff-1", Role.RESTAURANT);
        when(loginService.findSession("session-2")).thenReturn(Optional.of(staffSession));
        when(loginService.findAccount("staff-1")).thenReturn(Optional.of(new BakewayAccount(
                "staff-1", "staff1", "encoded", "Sweet Treats Staff", "", null, Role.RESTAURANT, "bakery-1")));

        ResponseEntity<?> response = loginController.getSession("session-2");

        assertEquals(new SessionResponse("staff-1", Role.RESTAURANT, "staff1", "Sweet Treats Staff", "bakery-1"),
                response.getBody());
    }
}
