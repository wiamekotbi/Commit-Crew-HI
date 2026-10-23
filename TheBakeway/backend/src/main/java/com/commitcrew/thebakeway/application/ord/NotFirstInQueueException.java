package com.commitcrew.thebakeway.application.ord;

// Only the first order of a bakery's preparation queue can start preparing (R-ORD-03)
public class NotFirstInQueueException extends IllegalStateException {

    public NotFirstInQueueException() {
        super("Order is not first in its bakery's preparation queue");
    }
}
