package com.commitcrew.thebakeway.application.ord;

import java.util.List;

import com.commitcrew.thebakeway.domain.bakery.Bakery;
import com.commitcrew.thebakeway.domain.menu.MenuItem;

// The bakeries and menu items read from the city config file (UC-ORD-01)
public record CityConfiguration(List<Bakery> bakeries, List<MenuItem> menuItems) {
}
