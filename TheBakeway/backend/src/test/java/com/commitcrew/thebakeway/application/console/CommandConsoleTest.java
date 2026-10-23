package com.commitcrew.thebakeway.application.console;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class CommandConsoleTest {

    private final CommandConsole console = new CommandConsole();

    @Test
    void recordsCommandsAndErrorsInOrder() {
        console.record("customer1", "CUSTOMER", "PLACE_ORDER", true, "order 1234");
        console.record("staff1", "RESTAURANT", "MARK_READY", false, "HTTP 409: Cannot mark ready an order that is placed");

        List<ConsoleEntry> entries = console.entriesAfter(0);

        assertEquals(2, entries.size());
        assertEquals("PLACE_ORDER", entries.get(0).command());
        assertTrue(entries.get(0).success());
        assertEquals("MARK_READY", entries.get(1).command());
        assertFalse(entries.get(1).success());
        assertEquals(2, entries.get(1).sequence());
    }

    @Test
    void returnsOnlyNewerEntries() {
        console.record("a", "CUSTOMER", "LOGIN", true, "");
        console.record("b", "CUSTOMER", "LOGIN", true, "");
        console.record("c", "CUSTOMER", "LOGIN", true, "");

        List<ConsoleEntry> entries = console.entriesAfter(2);

        assertEquals(1, entries.size());
        assertEquals("c", entries.get(0).user());
    }

    @Test
    void keepsOnlyTheLatestEntries() {
        for (int i = 0; i < CommandConsole.MAX_ENTRIES + 10; i++) {
            console.record("user", "CUSTOMER", "BROWSE_BAKERIES", true, "");
        }

        List<ConsoleEntry> entries = console.entriesAfter(0);

        assertEquals(CommandConsole.MAX_ENTRIES, entries.size());
        assertEquals(11, entries.get(0).sequence());
    }
}
