package com.commitcrew.thebakeway.application.prc;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class PromoValidationServiceTest {

    private final PromoValidationService service = new PromoValidationService(
            Clock.fixed(Instant.parse("2026-11-18T12:00:00Z"), ZoneOffset.UTC));
    private final BigDecimal minimum = new BigDecimal("25.00");

    @Test
    void acceptsValidPromotion() {
        assertDoesNotThrow(() -> service.validate(LocalDate.of(2026, 11, 19), minimum, new BigDecimal("30.00")));
    }

    @Test
    void rejectsPromotionOnDayAfterExpiration() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.validate(LocalDate.of(2026, 11, 17), minimum, new BigDecimal("30.00")));
        assertEquals("This promo code has expired", exception.getMessage());
    }

    @Test
    void acceptsPromotionOnExpirationDate() {
        assertDoesNotThrow(() -> service.validate(LocalDate.of(2026, 11, 18), minimum, new BigDecimal("30.00")));
    }

    @Test
    void rejectsOrderBelowMinimum() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.validate(LocalDate.of(2026, 11, 19), minimum, new BigDecimal("24.99")));
        assertEquals("This promo code requires an items subtotal of at least $25.00", exception.getMessage());
    }

    @Test
    void acceptsOrderExactlyAtMinimum() {
        assertDoesNotThrow(() -> service.validate(LocalDate.of(2026, 11, 19), minimum, new BigDecimal("25.0")));
    }

    @Test
    void acceptsOrderAboveMinimum() {
        assertDoesNotThrow(() -> service.validate(LocalDate.of(2026, 11, 19), minimum, new BigDecimal("25.01")));
    }

    @Test
    void rejectsInvalidInputs() {
        LocalDate expiration = LocalDate.of(2026, 11, 19);
        assertThrows(IllegalArgumentException.class, () -> service.validate(null, minimum, minimum));
        assertThrows(IllegalArgumentException.class, () -> service.validate(expiration, null, minimum));
        assertThrows(IllegalArgumentException.class, () -> service.validate(expiration, new BigDecimal("-1"), minimum));
        assertThrows(IllegalArgumentException.class, () -> service.validate(expiration, minimum, null));
        assertThrows(IllegalArgumentException.class, () -> service.validate(expiration, minimum, new BigDecimal("-1")));
    }
}
