package com.commitcrew.thebakeway.ui.registration;

public record RegistrationRequest(
        String name,
        String username,
        String password,
        String confirmPassword,
        String deliveryAddress) {
}