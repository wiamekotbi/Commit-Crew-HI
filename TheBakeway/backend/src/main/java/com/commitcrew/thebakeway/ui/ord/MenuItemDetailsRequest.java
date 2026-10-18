package com.commitcrew.thebakeway.ui.ord;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * HTTP input shared by menu-item creation and detail editing.
 * Bakery ownership is intentionally absent: the application derives it from
 * the authenticated restaurant account instead of trusting request data.
 */
public record MenuItemDetailsRequest(
        @NotBlank(message = "Menu item name is required")
        String name,

        String description,

        @NotNull(message = "Menu item price is required")
        @PositiveOrZero(message = "Menu item price cannot be negative")
        Double price) {
}
