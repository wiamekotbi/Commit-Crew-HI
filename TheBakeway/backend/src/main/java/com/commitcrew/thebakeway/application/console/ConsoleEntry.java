package com.commitcrew.thebakeway.application.console;

import java.time.Instant;

// One line of the output console: who ran which command, and whether it worked
public record ConsoleEntry(
        long sequence,
        Instant time,
        String user,
        String role,
        String command,
        boolean success,
        String detail) {
}
