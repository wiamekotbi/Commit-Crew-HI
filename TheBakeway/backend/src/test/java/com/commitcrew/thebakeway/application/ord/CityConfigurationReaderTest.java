package com.commitcrew.thebakeway.application.ord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

import com.commitcrew.thebakeway.domain.bakery.Bakery;
import com.commitcrew.thebakeway.domain.bakery.BakeryStatus;
import com.commitcrew.thebakeway.domain.courier.Courier;
import com.commitcrew.thebakeway.domain.menu.MenuItem;
import com.commitcrew.thebakeway.domain.menu.MenuItemStatus;

class CityConfigurationReaderTest {

    private static final String BAKERY =
            "BAKERY,bakery-1,Sweet Treats,open,123 Main Street,45.4972,-73.5789";

    private final CityConfigurationReader reader = new CityConfigurationReader();

    @Test
    void readsTheShippedCityFile() throws IOException {
        try (InputStream input = new ClassPathResource(InitialStateService.CITY_FILE).getInputStream()) {
            CityConfiguration city = reader.read(new Scanner(input));

            assertEquals(4, city.bakeries().size());
            assertEquals(9, city.menuItems().size());
            assertEquals(1, city.couriers().size());
            assertTrue(city.bakeries().stream().anyMatch(b -> b.getBakeryStatus() == BakeryStatus.paused));
            assertTrue(city.menuItems().stream().anyMatch(i -> i.getStatus() == MenuItemStatus.sold_out));
            assertEquals("acc-courier-1", city.couriers().get(0).getCourierId());
        }
    }

    @Test
    void readsBakeriesMenuItemsAndCouriersWithTheirLocations() {
        CityConfiguration city = read(
                "# comment line",
                "",
                BAKERY,
                "COURIER,courier-1,45.5048,-73.5772",
                "MENU_ITEM,item-1,bakery-1,Croissant,Butter croissant,3.50,sold_out");

        Bakery bakery = city.bakeries().get(0);
        assertEquals("bakery-1", bakery.getBakeryId());
        assertEquals("Sweet Treats", bakery.getBakeryName());
        assertEquals(BakeryStatus.open, bakery.getBakeryStatus());
        assertEquals("123 Main Street", bakery.getBakeryAddress());
        assertEquals(45.4972, bakery.getCoordinates().getLatitude());
        assertEquals(-73.5789, bakery.getCoordinates().getLongitude());

        Courier courier = city.couriers().get(0);
        assertEquals("courier-1", courier.getCourierId());
        assertEquals(45.5048, courier.getCoordinates().getLatitude());
        assertEquals(-73.5772, courier.getCoordinates().getLongitude());

        MenuItem item = city.menuItems().get(0);
        assertEquals("item-1", item.getId());
        assertEquals("bakery-1", item.getBakeryId());
        assertEquals("Croissant", item.getName());
        assertEquals(3.50, item.getPrice(), 0.001);
        assertEquals(MenuItemStatus.sold_out, item.getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "BAKERY,bakery-2,Crumbs,sleeping,45 Oak Avenue,45.5,-73.5",
            "BAKERY,bakery-2,Crumbs,open,,45.5,-73.5",
            "BAKERY,bakery-2,,open,45 Oak Avenue,45.5,-73.5",
            "BAKERY,bakery-1,Duplicate,open,45 Oak Avenue,45.5,-73.5",
            "BAKERY,bakery-2,Crumbs,open",
            "BAKERY,bakery-2,Crumbs,open,45 Oak Avenue,north,-73.5",
            "BAKERY,bakery-2,Crumbs,open,45 Oak Avenue,91,-73.5",
            "MENU_ITEM,item-1,bakery-1,Croissant,Butter,-1,available",
            "MENU_ITEM,item-1,bakery-1,Croissant,Butter,cheap,available",
            "MENU_ITEM,item-1,bakery-1,Croissant,Butter,3.50,gone",
            "MENU_ITEM,item-1,bakery-9,Croissant,Butter,3.50,available",
            "MENU_ITEM,item-1,bakery-1,,Butter,3.50,available",
            "COURIER,courier-1",
    })
    void refusesTheWholeFileForAnInvalidEntry(String invalidLine) {
        InvalidConfigurationException exception = assertThrows(InvalidConfigurationException.class,
                () -> read(BAKERY, invalidLine));

        assertTrue(exception.getMessage().contains("line 2"), exception.getMessage());
    }

    @Test
    void refusesDuplicateMenuItemIds() {
        assertThrows(InvalidConfigurationException.class, () -> read(
                BAKERY,
                "MENU_ITEM,item-1,bakery-1,Croissant,Butter,3.50,available",
                "MENU_ITEM,item-1,bakery-1,Baguette,Bread,4.00,available"));
    }

    @Test
    void refusesDuplicateCourierIds() {
        assertThrows(InvalidConfigurationException.class, () -> read(
                BAKERY,
                "COURIER,courier-1,45.5048,-73.5772",
                "COURIER,courier-1,45.5100,-73.5800"));
    }

    @Test
    void refusesCourierWithInvalidCoordinates() {
        InvalidConfigurationException exception = assertThrows(
                InvalidConfigurationException.class,
                () -> read(BAKERY, "COURIER,courier-1,45.5,-181"));

        assertTrue(exception.getMessage().contains("line 2"));
    }

    @Test
    void refusesAFileWithoutBakery() {
        assertThrows(InvalidConfigurationException.class, () -> read("# nothing here"));
    }

    private CityConfiguration read(String... lines) {
        return reader.read(new Scanner(String.join("\n", lines)));
    }
}
