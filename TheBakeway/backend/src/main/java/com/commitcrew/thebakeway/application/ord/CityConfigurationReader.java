package com.commitcrew.thebakeway.application.ord;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.commitcrew.thebakeway.domain.bakery.Bakery;
import com.commitcrew.thebakeway.domain.bakery.BakeryStatus;
import com.commitcrew.thebakeway.domain.courier.Courier;
import com.commitcrew.thebakeway.domain.location.Coordinates;
import com.commitcrew.thebakeway.domain.menu.MenuItem;
import com.commitcrew.thebakeway.domain.menu.MenuItemStatus;

// Reads the city part of the config file (UC-ORD-01). Same line format as the accounts file:
//   BAKERY,bakeryId,name,status,address,latitude,longitude
//   MENU_ITEM,itemId,bakeryId,name,description,price,status
//   COURIER,courierId,latitude,longitude,status (offline | idle)
// Blank lines and lines starting with # are ignored. Any invalid entry refuses the whole file.
@Component
public class CityConfigurationReader {

    public CityConfiguration read(Scanner scanner) {
        List<Bakery> bakeries = new ArrayList<>();
        List<MenuItem> menuItems = new ArrayList<>();
        List<Courier> couriers = new ArrayList<>();
        Set<String> bakeryIds = new HashSet<>();
        Set<String> menuItemIds = new HashSet<>();
        Set<String> courierIds = new HashSet<>();

        int lineNumber = 0;
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            lineNumber++;

            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            String[] values = line.split(",", -1);
            switch (values[0]) {
                case "BAKERY" -> bakeries.add(bakery(values, lineNumber, bakeryIds));
                case "MENU_ITEM" -> menuItems.add(menuItem(values, lineNumber, bakeryIds, menuItemIds));
                case "COURIER" -> couriers.add(courier(values, lineNumber, courierIds));
                default -> throw invalid(lineNumber, "unknown entry type '" + values[0] + "'");
            }
        }

        if (bakeries.isEmpty()) {
            throw new InvalidConfigurationException("City config has no bakery");
        }

        return new CityConfiguration(bakeries, menuItems, couriers);
    }

    private Bakery bakery(String[] values, int lineNumber, Set<String> bakeryIds) {
        requireFieldCount(values, 7, lineNumber);
        String id = required(values[1], "bakery id", lineNumber);

        if (!bakeryIds.add(id)) {
            throw invalid(lineNumber, "duplicate bakery id '" + id + "'");
        }

        return new Bakery(
                id,
                required(values[2], "bakery name", lineNumber),
                bakeryStatus(values[3], lineNumber),
                required(values[4], "bakery address", lineNumber),
                coordinates(values[5], values[6], lineNumber));
    }

    private Courier courier(String[] values, int lineNumber, Set<String> courierIds) {
        requireFieldCount(values, 5, lineNumber);
        String id = required(values[1], "courier id", lineNumber);

        if (!courierIds.add(id)) {
            throw invalid(lineNumber, "duplicate courier id '" + id + "'");
        }

        Courier courier = new Courier(id, coordinates(values[2], values[3], lineNumber));

        // R-ORD-01: the file gives each courier's starting status; a courier
        // starts on duty (idle) or off duty (offline), never with an order
        switch (values[4].trim()) {
            case "offline" -> { }
            case "idle" -> courier.goOnDuty();
            default -> throw invalid(lineNumber, "unknown courier status '" + values[4].trim() + "'");
        }

        return courier;
    }

    // Menu items must come after the bakery they belong to
    private MenuItem menuItem(String[] values, int lineNumber, Set<String> bakeryIds, Set<String> menuItemIds) {
        requireFieldCount(values, 7, lineNumber);
        String id = required(values[1], "menu item id", lineNumber);
        String bakeryId = required(values[2], "bakery id", lineNumber);

        if (!menuItemIds.add(id)) {
            throw invalid(lineNumber, "duplicate menu item id '" + id + "'");
        }

        if (!bakeryIds.contains(bakeryId)) {
            throw invalid(lineNumber, "unknown bakery '" + bakeryId + "'");
        }

        return new MenuItem(
                id,
                bakeryId,
                required(values[3], "menu item name", lineNumber),
                values[4].trim(),
                price(values[5], lineNumber),
                menuItemStatus(values[6], lineNumber));
    }

    private void requireFieldCount(String[] values, int expected, int lineNumber) {
        if (values.length != expected) {
            throw invalid(lineNumber, "expected " + expected + " values but found " + values.length);
        }
    }

    private String required(String value, String field, int lineNumber) {
        if (value.isBlank()) {
            throw invalid(lineNumber, field + " is missing");
        }
        return value.trim();
    }

    private BakeryStatus bakeryStatus(String value, int lineNumber) {
        try {
            return BakeryStatus.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            throw invalid(lineNumber, "bakery status '" + value + "' does not exist");
        }
    }

    private MenuItemStatus menuItemStatus(String value, int lineNumber) {
        try {
            return MenuItemStatus.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            throw invalid(lineNumber, "menu item status '" + value + "' does not exist");
        }
    }

    private double price(String value, int lineNumber) {
        double price;
        try {
            price = Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw invalid(lineNumber, "price '" + value + "' is not a number");
        }

        if (price < 0) {
            throw invalid(lineNumber, "price cannot be negative");
        }
        return price;
    }

    private Coordinates coordinates(String latitudeValue, String longitudeValue, int lineNumber) {
        double latitude;
        double longitude;
        try {
            latitude = Double.parseDouble(latitudeValue.trim());
            longitude = Double.parseDouble(longitudeValue.trim());
        } catch (NumberFormatException e) {
            throw invalid(lineNumber, "latitude and longitude must be numbers");
        }

        try {
            return new Coordinates(latitude, longitude);
        } catch (IllegalArgumentException e) {
            // Preserve the coordinate validation reason while adding the config
            // line number needed to locate and correct the invalid entry.
            throw invalid(lineNumber, e.getMessage());
        }
    }

    private InvalidConfigurationException invalid(int lineNumber, String reason) {
        return new InvalidConfigurationException("Invalid city config entry at line " + lineNumber + ": " + reason);
    }
}
