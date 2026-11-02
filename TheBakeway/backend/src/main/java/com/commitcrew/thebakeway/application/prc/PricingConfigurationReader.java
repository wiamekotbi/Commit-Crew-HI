
package com.commitcrew.thebakeway.application.prc;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class PricingConfigurationReader {

    public PricingConfiguration read() {
        Properties properties = new Properties();

        try (InputStream input = new ClassPathResource("config/pricing.txt").getInputStream()) {
            properties.load(input);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read pricing configuration", e);
        }

        double deliveryFee = getValue(properties, "deliveryFee");
        double serviceFee = getValue(properties, "serviceFee");
        double threshold = getValue(properties, "smallOrderThreshold");
        double surcharge = getValue(properties, "smallOrderSurcharge");

        return new PricingConfiguration(
                deliveryFee, serviceFee, threshold, surcharge);
    }

    private double getValue(Properties properties, String key) {
        String value = properties.getProperty(key);

        if (value == null) {
            throw new IllegalArgumentException("Missing pricing value: " + key);
        }

        double number;

        try {
            number = Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid pricing value: " + key);
        }

        if (!Double.isFinite(number) || number < 0) {
            throw new IllegalArgumentException("Invalid pricing value: " + key);
        }

        return number;
    }
}
