package com.commitcrew.thebakeway.application.prc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class PromoDiscountServiceTest {

    private final PromoConfigurationReader reader = mock(PromoConfigurationReader.class);
    private final PromoValidationService validator = mock(PromoValidationService.class);
    private final PromoDiscountService service = new PromoDiscountService(reader, validator);

    private final PromoConfiguration saveFive = new PromoConfiguration(
            "SAVE5",
            LocalDate.of(2027, 12, 31),
            new BigDecimal("20.00"),
            new BigDecimal("5.00"));

    @Test
    void appliesConfiguredFixedDiscount() {
        when(reader.read()).thenReturn(List.of(saveFive));

        PromoDiscountResult result = service.applyDiscount(
                "SAVE5",
                new BigDecimal("25.00"),
                new BigDecimal("31.00"));

        assertEquals("SAVE5", result.promoCode());
        assertEquals(new BigDecimal("5.00"), result.discountAmount());
        assertEquals(new BigDecimal("26.00"), result.discountedTotal());
        verify(validator).validate(
                saveFive.expirationDate(),
                saveFive.minimumOrderAmount(),
                new BigDecimal("25.00"));
    }

    @Test
    void matchesCodeIgnoringCaseAndSurroundingSpaces() {
        when(reader.read()).thenReturn(List.of(saveFive));

        PromoDiscountResult result = service.applyDiscount(
                "  save5  ",
                new BigDecimal("20.00"),
                new BigDecimal("26.00"));

        assertEquals("SAVE5", result.promoCode());
        assertEquals(new BigDecimal("21.00"), result.discountedTotal());
    }

    @Test
    void rejectsUnknownCodeWithoutRunningPromotionValidation() {
        when(reader.read()).thenReturn(List.of(saveFive));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.applyDiscount("NOTREAL", new BigDecimal("25.00"), new BigDecimal("31.00")));

        assertEquals("Unknown promo code", exception.getMessage());
        verifyNoInteractions(validator);
    }

    @Test
    void rejectsBlankCodeBeforeReadingConfiguration() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.applyDiscount("  ", new BigDecimal("25.00"), new BigDecimal("31.00")));

        assertEquals("Promo code cannot be null or blank", exception.getMessage());
        verifyNoInteractions(reader, validator);
    }

    @Test
    void rejectsNullOrNegativeCurrentTotalBeforeReadingConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> service.applyDiscount("SAVE5", new BigDecimal("25.00"), null));
        assertThrows(IllegalArgumentException.class,
                () -> service.applyDiscount("SAVE5", new BigDecimal("25.00"), new BigDecimal("-0.01")));

        verifyNoInteractions(reader, validator);
    }

    @Test
    void capsDiscountSoTotalCannotBecomeNegative() {
        when(reader.read()).thenReturn(List.of(saveFive));

        PromoDiscountResult result = service.applyDiscount(
                "SAVE5",
                new BigDecimal("20.00"),
                new BigDecimal("3.00"));

        assertEquals(new BigDecimal("3.00"), result.discountAmount());
        assertEquals(new BigDecimal("0.00"), result.discountedTotal());
    }

    @Test
    void propagatesExistingValidationError() {
        when(reader.read()).thenReturn(List.of(saveFive));
        IllegalArgumentException validationError = new IllegalArgumentException("This promo code has expired");
        doThrow(validationError).when(validator).validate(
                saveFive.expirationDate(),
                saveFive.minimumOrderAmount(),
                new BigDecimal("25.00"));

        IllegalArgumentException result = assertThrows(IllegalArgumentException.class,
                () -> service.applyDiscount("SAVE5", new BigDecimal("25.00"), new BigDecimal("31.00")));

        assertSame(validationError, result);
    }
}
