package com.commitcrew.thebakeway.ui.registration;

public record ProfileUpdateRequest(
        String displayName,
        String contactEmail,
        String deliveryAddress) {
}