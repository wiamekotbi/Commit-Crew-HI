package com.commitcrew.thebakeway.application.reg;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.commitcrew.thebakeway.domain.account.AccountRegistry;
import com.commitcrew.thebakeway.domain.account.BakewayAccount;
import com.commitcrew.thebakeway.domain.session.Session;

@Service
public class LoginService {

    private static final String INVALID_CREDENTIALS = "Invalid username or password";

    private final AccountRegistry accountRegistry;
    private final PasswordEncoder passwordEncoder;
    private final SessionManager sessionManager;

    public LoginService(
            AccountRegistry accountRegistry,
            PasswordEncoder passwordEncoder,
            SessionManager sessionManager) {
        this.accountRegistry = accountRegistry;
        this.passwordEncoder = passwordEncoder;
        this.sessionManager = sessionManager;
    }

    public Session login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException(INVALID_CREDENTIALS);
        }

        BakewayAccount account = accountRegistry.findByUsername(username);
        if (account == null || !passwordEncoder.matches(password, account.getPassword())) {
            throw new IllegalArgumentException(INVALID_CREDENTIALS);
        }

        return sessionManager.createSession(account.getAccountId(), account.getRole());
    }

    public Optional<Session> findSession(String sessionId) {
        return sessionManager.findSession(sessionId);
    }

    public void logout(String sessionId) {
        sessionManager.invalidateSession(sessionId);
    }
}
