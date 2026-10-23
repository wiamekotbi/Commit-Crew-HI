package com.commitcrew.thebakeway.ui.ord;

// The reason is required; OrderService refuses a blank one (UC-ORD-06)
public record RejectOrderRequest(String reason) {
}
