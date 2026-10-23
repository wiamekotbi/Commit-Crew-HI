package com.commitcrew.thebakeway.ui.console;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.commitcrew.thebakeway.application.console.CommandConsole;
import com.commitcrew.thebakeway.application.console.ConsoleEntry;
import com.commitcrew.thebakeway.application.reg.SessionManager;

// The dashboard's console panel: entries newer than "after". Any logged-in user can read it (R-REG-02).
@RestController
@RequestMapping("/api/console")
public class ConsoleController {

    private static final String SESSION_COOKIE = "BAKEWAY_SESSION_ID";

    private final CommandConsole console;
    private final SessionManager sessionManager;

    public ConsoleController(CommandConsole console, SessionManager sessionManager) {
        this.console = console;
        this.sessionManager = sessionManager;
    }

    @GetMapping
    public ResponseEntity<?> entries(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionId,
            @RequestParam(value = "after", defaultValue = "0") long after) {
        if (sessionManager.findSession(sessionId).isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
        }
        List<ConsoleEntry> entries = console.entriesAfter(after);
        return ResponseEntity.ok(entries);
    }
}
