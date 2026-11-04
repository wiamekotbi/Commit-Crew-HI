package com.commitcrew.thebakeway.application.ord;

import java.util.List;

import com.commitcrew.thebakeway.domain.bakery.Bakery;
import com.commitcrew.thebakeway.domain.courier.Courier;
import com.commitcrew.thebakeway.domain.menu.MenuItem;

// The bakeries, menu items, and courier locations read from the city config file.
// Courier selection uses this same initial-state source so dispatch does not
// depend on hard-coded locations in application services.
public record CityConfiguration(
        List<Bakery> bakeries,
        List<MenuItem> menuItems,
        List<Courier> couriers) {
}
