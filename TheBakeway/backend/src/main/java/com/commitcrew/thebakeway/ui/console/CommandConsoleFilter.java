package com.commitcrew.thebakeway.ui.console;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.commitcrew.thebakeway.application.console.CommandConsole;
import com.commitcrew.thebakeway.application.reg.SessionManager;
import com.commitcrew.thebakeway.domain.account.AccountRegistry;
import com.commitcrew.thebakeway.domain.account.BakewayAccount;
import com.commitcrew.thebakeway.domain.session.Session;

// Writes every /api command and its result to the output console, in one place for all controllers.
// Errors are taken from the {"error": "..."} body the controllers return. Passwords are never logged.
@Component
public class CommandConsoleFilter extends OncePerRequestFilter {

    private static final String SESSION_COOKIE = "BAKEWAY_SESSION_ID";
    private static final String CONSOLE_PATH = "/api/console";
    private static final int MAX_BODY_BYTES = 64 * 1024;

    private final CommandConsole console;
    private final SessionManager sessionManager;
    private final AccountRegistry accountRegistry;

    public CommandConsoleFilter(CommandConsole console, SessionManager sessionManager, AccountRegistry accountRegistry) {
        this.console = console;
        this.sessionManager = sessionManager;
        this.accountRegistry = accountRegistry;
    }

    // Only API commands; reading the console itself is not logged, or it would fill itself up
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/") || path.equals(CONSOLE_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper cachedRequest = new ContentCachingRequestWrapper(request, MAX_BODY_BYTES);
        ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(response);
        String command = CommandNames.of(request.getMethod(), request.getRequestURI());

        // Who is calling: resolved before the command runs, because logout and restore end the session
        String[] caller = caller(request);

        try {
            chain.doFilter(cachedRequest, cachedResponse);
        } catch (IOException | ServletException | RuntimeException e) {
            console.record(caller[0], caller[1], command, false, "HTTP 500: " + e.getMessage());
            throw e;
        }

        String body = new String(cachedResponse.getContentAsByteArray(), StandardCharsets.UTF_8);
        int status = cachedResponse.getStatus();
        cachedResponse.copyBodyToResponse();

        if (caller[0].equals("anonymous")) {
            // Login and registration: name the user from the request (never the password) and the role from the reply
            String requestBody = new String(cachedRequest.getContentAsByteArray(), StandardCharsets.UTF_8);
            caller[0] = field(requestBody, "username").orElse("anonymous");
            caller[1] = field(body, "role").orElse("-");
        }

        boolean success = status < 400;
        console.record(caller[0], caller[1], command, success, success ? summary(request, body) : error(status, body));
    }

    private String[] caller(HttpServletRequest request) {
        Optional<Session> session = sessionManager.findSession(sessionId(request));
        if (session.isEmpty()) {
            return new String[] {"anonymous", "-"};
        }
        String user = accountRegistry.findById(session.get().getAccountId())
                .map(BakewayAccount::getUsername)
                .orElse(session.get().getAccountId());
        return new String[] {user, session.get().getRole().name()};
    }

    private String sessionId(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (SESSION_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    // Reads: the request and how many results. Commands: the order, its state change, the event id
    // and the queue position, when the reply has them.
    private String summary(HttpServletRequest request, String body) {
        String target = request.getMethod() + " " + request.getRequestURI()
                + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
        if ("GET".equals(request.getMethod())) {
            return body.startsWith("[") ? target + " | " + count(body) + " result(s)" : target;
        }
        if (field(body, "orderId").isEmpty()) {
            return target;
        }

        StringBuilder summary = new StringBuilder();
        field(body, "orderId").ifPresent(id -> summary.append("order ").append(shortId(id)));
        field(body, "previousState").ifPresent(previous -> summary.append(" | ").append(previous).append(" ->"));
        field(body, "newState").ifPresent(state -> summary.append(field(body, "previousState").isPresent() ? " " : " | ")
                .append(state));
        field(body, "eventId").ifPresent(id -> summary.append(" | event ").append(shortId(id)));
        field(body, "queuePosition").ifPresent(position -> summary.append(" | queue position ").append(position));
        return summary.toString();
    }

    // Number of top-level items in a JSON array reply
    static int count(String jsonArray) {
        int depth = 0;
        int items = 0;
        boolean inString = false;
        for (int i = 0; i < jsonArray.length(); i++) {
            char c = jsonArray.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == '[' || c == '{') {
                if (depth == 1 && items == 0) {
                    items = 1;
                }
                depth++;
            } else if (c == ']' || c == '}') {
                depth--;
            } else if (c == ',' && depth == 1) {
                items++;
            } else if (depth == 1 && items == 0 && !Character.isWhitespace(c)) {
                items = 1;
            }
        }
        return items;
    }

    private String error(int status, String body) {
        return "HTTP " + status + ": " + field(body, "error").orElse("request refused");
    }

    private String shortId(String id) {
        return id.length() > 8 ? id.substring(0, 8) : id;
    }

    // Reads one top-level string or number field from a JSON reply; null values count as absent
    static Optional<String> field(String json, String name) {
        Matcher matcher = Pattern
                .compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*(?:\"((?:[^\"\\\\]|\\\\.)*)\"|(-?[0-9.]+))")
                .matcher(json);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(1) != null ? matcher.group(1).replace("\\\"", "\"") : matcher.group(2));
    }
}
