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
import com.commitcrew.thebakeway.domain.menu.MenuItem;
import com.commitcrew.thebakeway.domain.menu.MenuItemStatus;

class CityConfigurationReaderTest {

    private static final String BAKERY = "BAKERY,bakery-1,Sweet Treats,open,123 Main Street";

    private final CityConfigurationReader reader = new CityConfigurationReader();

    @Test
    void readsTheShippedCityFile() throws IOException {
        try (InputStream input = new ClassPathResource(InitialStateService.CITY_FILE).getInputStream()) {
            CityConfiguration city = reader.read(new Scanner(input));

            assertEquals(4, city.bakeries().size());
            assertEquals(9, city.menuItems().size());
            assertTrue(city.bakeries().stream().anyMatch(b -> b.getBakeryStatus() == BakeryStatus.paused));
            assertTrue(city.menuItems().stream().anyMatch(i -> i.getStatus() == MenuItemStatus.sold_out));
        }
    }

    @Test
    void readsBakeriesAndMenuItemsWithTheirIds() {
        CityConfiguration city = read(
                "# comment line",
                "",
                BAKERY,
                "MENU_ITEM,item-1,bakery-1,Croissant,Butter croissant,3.50,sold_out");

        Bakery bakery = city.bakeries().get(0);
        assertEquals("bakery-1", bakery.getBakeryId());
        assertEquals("Sweet Treats", bakery.getBakeryName());
        assertEquals(BakeryStatus.open, bakery.getBakeryStatus());
        assertEquals("123 Main Street", bakery.getBakeryAddress());

        MenuItem item = city.menuItems().get(0);
        assertEquals("item-1", item.getId());
        assertEquals("bakery-1", item.getBakeryId());
        assertEquals("Croissant", item.getName());
        assertEquals(3.50, item.getPrice(), 0.001);
        assertEquals(MenuItemStatus.sold_out, item.getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "BAKERY,bakery-2,Crumbs,sleeping,45 Oak Avenue",
            "BAKERY,bakery-2,Crumbs,open,",
            "BAKERY,bakery-2,,open,45 Oak Avenue",
            "BAKERY,bakery-1,Duplicate,open,45 Oak Avenue",
            "BAKERY,bakery-2,Crumbs,open",
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
    void refusesAFileWithoutBakery() {
        assertThrows(InvalidConfigurationException.class, () -> read("# nothing here"));
    }

    private CityConfiguration read(String... lines) {
        return reader.read(new Scanner(String.join("\n", lines)));
    }
}
