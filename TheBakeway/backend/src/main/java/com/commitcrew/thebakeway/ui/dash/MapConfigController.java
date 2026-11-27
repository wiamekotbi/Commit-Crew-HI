package com.commitcrew.thebakeway.ui.dash;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.commitcrew.thebakeway.application.reg.SessionManager;

// Gives the dashboard the Google Maps browser key at runtime, so the key is never committed.
// It comes from the GOOGLE_MAPS_API_KEY environment variable (a platform secret in the demo).
// A browser key is always visible to the page that uses it: it is protected by the HTTP referrer
// and API restrictions set on the key in Google Cloud, and here only logged-in users receive it.
@RestController
@RequestMapping("/api/map")
public class MapConfigController {

    private static final String SESSION_COOKIE = "BAKEWAY_SESSION_ID";

    private final SessionManager sessionManager;
    private final String apiKey;

    public MapConfigController(
            SessionManager sessionManager,
            @Value("${bakeway.maps.api-key:}") String apiKey) {
        this.sessionManager = sessionManager;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, String>> getConfig(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionId) {
        if (sessionManager.findSession(sessionId).isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
        }
        // An empty key means the map is not configured; the dashboard then shows a message
        return ResponseEntity.ok(Map.of("apiKey", apiKey));
    }
}
