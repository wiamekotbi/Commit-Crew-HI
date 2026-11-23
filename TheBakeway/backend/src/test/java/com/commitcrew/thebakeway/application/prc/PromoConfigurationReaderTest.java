package com.commitcrew.thebakeway.application.prc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

import org.junit.jupiter.api.Test;

class PromoConfigurationReaderTest {

    private final PromoConfigurationReader reader = new PromoConfigurationReader();

    @Test
    void readsValidConfigurations() {
        List<PromoConfiguration> promos = read(
                "# promotions\n SAVE , 2027-12-31 , 25.50 , 5.00 \nOTHER,2028-01-01,0,10\n");
        assertEquals(List.of(
                new PromoConfiguration("SAVE", LocalDate.of(2027, 12, 31),
                        new BigDecimal("25.50"), new BigDecimal("5.00")),
                new PromoConfiguration("OTHER", LocalDate.of(2028, 1, 1),
                        BigDecimal.ZERO, new BigDecimal("10"))), promos);
    }

    @Test
    void readsDefaultConfigurationFile() {
        assertNotNull(reader.read());
    }

    @Test
    void rejectsMissingFields() {
        for (String line : List.of(
                ",2027-12-31,25,5",
                "SAVE,,25,5",
                "SAVE,2027-12-31,,5",
                "SAVE,2027-12-31,25,",
                "SAVE,2027-12-31,25")) {
            assertThrows(IllegalArgumentException.class, () -> read(line));
        }
    }

    @Test
    void rejectsInvalidDates() {
        assertThrows(IllegalArgumentException.class, () -> read("SAVE,2027-02-30,25,5"));
        assertThrows(IllegalArgumentException.class, () -> read("SAVE,not-a-date,25,5"));
    }

    @Test
    void rejectsInvalidAmounts() {
        for (String amount : List.of("not-a-number", "NaN", "Infinity", "-0.01")) {
            assertThrows(IllegalArgumentException.class, () -> read("SAVE,2027-12-31," + amount + ",5"));
        }
    }

    @Test
    void rejectsInvalidDiscountAmounts() {
        for (String discount : List.of("not-a-number", "NaN", "Infinity", "0", "-0.01")) {
            assertThrows(IllegalArgumentException.class,
                    () -> read("SAVE,2027-12-31,25," + discount));
        }
    }

    @Test
    void rejectsDuplicateCodes() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> read("SAVE,2027-12-31,25,5\n SAVE ,2028-01-01,30,10"));
        assertTrue(exception.getMessage().contains("line 2"));
        assertTrue(exception.getMessage().contains("duplicate promo code"));
    }

    private List<PromoConfiguration> read(String content) {
        try (Scanner scanner = new Scanner(content)) {
            return reader.read(scanner);
        }
    }
}
