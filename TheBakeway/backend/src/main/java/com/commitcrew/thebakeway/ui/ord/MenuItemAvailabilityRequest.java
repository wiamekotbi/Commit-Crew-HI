package com.commitcrew.thebakeway.ui.ord;

import jakarta.validation.constraints.NotNull;

import com.commitcrew.thebakeway.domain.menu.MenuItemStatus;

/** Accepts the explicit availability state selected by bakery staff. */
public record MenuItemAvailabilityRequest(
        @NotNull(message = "Menu item status is required")
        MenuItemStatus status) {
}
