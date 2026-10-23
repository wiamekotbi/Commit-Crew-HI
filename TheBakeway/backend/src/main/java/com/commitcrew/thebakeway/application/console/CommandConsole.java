package com.commitcrew.thebakeway.application.console;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// Output console: every command and every error, printed in the terminal and kept in memory
// (the last MAX_ENTRIES) so the dashboard can show them.
@Component
public class CommandConsole {

    static final int MAX_ENTRIES = 500;

    private static final Logger logger = LoggerFactory.getLogger("CONSOLE");

    private final Deque<ConsoleEntry> entries = new ArrayDeque<>();
    private long lastSequence = 0;

    public synchronized ConsoleEntry record(String user, String role, String command, boolean success, String detail) {
        ConsoleEntry entry = new ConsoleEntry(++lastSequence, Instant.now(), user, role, command, success, detail);

        entries.addLast(entry);
        if (entries.size() > MAX_ENTRIES) {
            entries.removeFirst();
        }

        String line = String.format("%-5s %s (%s) %s%s",
                success ? "OK" : "ERROR", user, role, command, detail == null || detail.isBlank() ? "" : " - " + detail);
        if (success) {
            logger.info(line);
        } else {
            logger.warn(line);
        }
        return entry;
    }

    // Entries newer than the given sequence number, oldest first (0 returns everything kept)
    public synchronized List<ConsoleEntry> entriesAfter(long sequence) {
        return entries.stream()
                .filter(entry -> entry.sequence() > sequence)
                .toList();
    }
}
