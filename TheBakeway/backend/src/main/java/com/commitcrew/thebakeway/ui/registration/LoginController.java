package com.commitcrew.thebakeway.ui.registration;

import java.time.Duration;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.commitcrew.thebakeway.application.reg.LoginService;
import com.commitcrew.thebakeway.domain.session.Session;

@RestController
@RequestMapping("/api")
public class LoginController {

    static final String SESSION_COOKIE = "BAKEWAY_SESSION_ID";

    private final LoginService loginService;

    public LoginController(LoginService loginService) {
        this.loginService = loginService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            Session session = loginService.login(request.username(), request.password());
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, sessionCookie(session.getSessionId()).toString())
                    .body(describe(session));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/session")
    public ResponseEntity<?> getSession(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionId) {
        return loginService.findSession(sessionId)
                .<ResponseEntity<?>>map(session -> ResponseEntity.ok(describe(session)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Authentication required")));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionId) {
        loginService.logout(sessionId);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredSessionCookie().toString())
                .body(Map.of("message", "Logged out"));
    }

    private SessionResponse describe(Session session) {
        return SessionResponse.from(session, loginService.findAccount(session.getAccountId()).orElse(null));
    }

    private ResponseCookie sessionCookie(String sessionId) {
        return ResponseCookie.from(SESSION_COOKIE, sessionId)
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .build();
    }

    private ResponseCookie expiredSessionCookie() {
        return ResponseCookie.from(SESSION_COOKIE, "")
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }
}
