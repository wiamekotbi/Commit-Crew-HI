package com.commitcrew.thebakeway.application.prc;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class PromoConfigurationReader {

    public List<PromoConfiguration> read() {
        try (Scanner scanner = new Scanner(new ClassPathResource("config/promos.txt").getInputStream(),
                java.nio.charset.StandardCharsets.UTF_8)) {
            return read(scanner);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read promo configuration", e);
        }
    }

    public List<PromoConfiguration> read(Scanner scanner) {
        List<PromoConfiguration> promos = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        int lineNumber = 0;
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            lineNumber++;
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] fields = line.split(",", -1);
            if (fields.length != 4 || fields[0].isBlank() || fields[1].isBlank()
                    || fields[2].isBlank() || fields[3].isBlank()) {
                throw invalid(lineNumber,
                        "code, expiration date, minimum order amount and discount amount are required");
            }
            String code = fields[0].trim();
            if (!codes.add(code)) {
                throw invalid(lineNumber, "duplicate promo code '" + code + "'");
            }
            LocalDate expiration;
            try {
                expiration = LocalDate.parse(fields[1].trim());
            } catch (DateTimeParseException e) {
                throw invalid(lineNumber, "invalid expiration date");
            }
            BigDecimal minimum;
            try {
                minimum = new BigDecimal(fields[2].trim());
            } catch (NumberFormatException e) {
                throw invalid(lineNumber, "invalid minimum order amount");
            }
            if (minimum.signum() < 0) {
                throw invalid(lineNumber, "minimum order amount cannot be negative");
            }
            BigDecimal discount;
            try {
                discount = new BigDecimal(fields[3].trim());
            } catch (NumberFormatException e) {
                throw invalid(lineNumber, "invalid discount amount");
            }
            if (discount.signum() <= 0) {
                throw invalid(lineNumber, "discount amount must be positive");
            }
            promos.add(new PromoConfiguration(code, expiration, minimum, discount));
        }
        return List.copyOf(promos);
    }

    private IllegalArgumentException invalid(int lineNumber, String reason) {
        return new IllegalArgumentException("Invalid promo config entry at line " + lineNumber + ": " + reason);
    }
}
