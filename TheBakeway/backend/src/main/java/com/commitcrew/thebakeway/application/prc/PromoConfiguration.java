package com.commitcrew.thebakeway.application.prc;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PromoConfiguration(String code, LocalDate expirationDate, BigDecimal minimumOrderAmount) {
}
