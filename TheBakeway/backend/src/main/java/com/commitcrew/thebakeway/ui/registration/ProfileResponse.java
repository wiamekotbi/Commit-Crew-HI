package com.commitcrew.thebakeway.ui.registration;

import com.commitcrew.thebakeway.domain.account.BakewayAccount;
import com.commitcrew.thebakeway.domain.account.Role;

public record ProfileResponse(
        String accountId,
        String username,
        String displayName,
        String contactEmail,
        String deliveryAddress,
        Role role) {

    public static ProfileResponse from(BakewayAccount account) {
        return new ProfileResponse(
                account.getAccountId(),
                account.getUsername(),
                account.getDisplayName(),
                account.getContactEmail(),
                account.getDeliveryAddress(),
                account.getRole());
    }
}