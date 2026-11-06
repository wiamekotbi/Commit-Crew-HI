package com.commitcrew.thebakeway.application.dlv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class DispatchWaitingQueueTest {

    private final DispatchWaitingQueue queue = new DispatchWaitingQueue();

    @Test
    void preservesFirstInFirstOutOrderWithoutDuplicates() {
        assertTrue(queue.enqueue("order-1"));
        assertTrue(queue.enqueue("order-2"));

        // Re-enqueueing a waiting order neither duplicates it nor moves it to
        // the back, preserving a fair and deterministic retry order.
        assertFalse(queue.enqueue("order-1"));

        assertEquals(List.of("order-1", "order-2"), queue.snapshot());
        assertEquals("order-1", queue.peek().orElseThrow());
    }

    @Test
    void removesOrderWhenItLeavesTheWaitingQueue() {
        queue.enqueue("order-1");
        queue.enqueue("order-2");

        assertTrue(queue.remove("order-1"));
        assertFalse(queue.contains("order-1"));
        assertEquals("order-2", queue.peek().orElseThrow());
        assertFalse(queue.remove("order-1"));
    }

    @Test
    void returnsEmptyWhenNobodyIsWaiting() {
        assertTrue(queue.peek().isEmpty());
        assertTrue(queue.snapshot().isEmpty());
    }

    @Test
    void clearsEveryEntryForReset() {
        queue.enqueue("order-1");
        queue.enqueue("order-2");

        queue.clear();

        assertTrue(queue.snapshot().isEmpty());
    }

    @Test
    void rejectsMissingOrderIds() {
        assertThrows(IllegalArgumentException.class, () -> queue.enqueue(null));
        assertThrows(IllegalArgumentException.class, () -> queue.enqueue(" "));
        assertThrows(IllegalArgumentException.class, () -> queue.remove(" "));
        assertThrows(IllegalArgumentException.class, () -> queue.contains(null));
    }
}
