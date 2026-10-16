package com.commitcrew.thebakeway.application.reg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.commitcrew.thebakeway.domain.account.AccountRegistry;
import com.commitcrew.thebakeway.domain.account.BakewayAccount;
import com.commitcrew.thebakeway.domain.account.Role;
import com.commitcrew.thebakeway.domain.session.Session;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    @Mock
    private AccountRegistry accountRegistry;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private SessionManager sessionManager;

    private LoginService loginService;

    @BeforeEach
    void setUp() {
        loginService = new LoginService(accountRegistry, passwordEncoder, sessionManager);
    }

    @Test
    void createsSessionForValidCredentials() {
        BakewayAccount account = account();
        Session expected = new Session("session-1", "account-1", Role.CUSTOMER);
        when(accountRegistry.findByUsername("maria")).thenReturn(account);
        when(passwordEncoder.matches("password", "encoded-password")).thenReturn(true);
        when(sessionManager.createSession("account-1", Role.CUSTOMER)).thenReturn(expected);

        Session actual = loginService.login("maria", "password");

        assertEquals(expected, actual);
    }

    @Test
    void rejectsUnknownUsernameWithoutCreatingSession() {
        when(accountRegistry.findByUsername("unknown")).thenReturn(null);

        assertThrows(IllegalArgumentException.class,
                () -> loginService.login("unknown", "password"));

        verify(sessionManager, never()).createSession(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsWrongPasswordWithoutCreatingSession() {
        BakewayAccount account = account();
        when(accountRegistry.findByUsername("maria")).thenReturn(account);
        when(passwordEncoder.matches("wrong", "encoded-password")).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> loginService.login("maria", "wrong"));

        verify(sessionManager, never()).createSession(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsBlankCredentialsWithoutCreatingSession() {
        assertThrows(IllegalArgumentException.class, () -> loginService.login("", ""));

        verify(sessionManager, never()).createSession(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    private BakewayAccount account() {
        return new BakewayAccount(
                "account-1",
                "maria",
                "encoded-password",
                "Maria",
                "",
                "Montreal",
                Role.CUSTOMER);
    }
}
