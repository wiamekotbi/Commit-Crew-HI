package com.commitcrew.thebakeway.application.reg;

import org.springframework.stereotype.Service;

import com.commitcrew.thebakeway.domain.account.AccountRegistry;
import com.commitcrew.thebakeway.domain.account.BakewayAccount;

@Service
public class ProfileService {

    private final AccountRegistry accountRegistry;

    public ProfileService(AccountRegistry accountRegistry) {
        this.accountRegistry = accountRegistry;
    }

    public BakewayAccount getProfile(String accountId) {
        return accountRegistry.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    public BakewayAccount updateProfile(
            String accountId,
            String displayName,
            String contactEmail,
            String deliveryAddress) {

        BakewayAccount account = getProfile(accountId);

        account.updateProfile(displayName, contactEmail, deliveryAddress);

        return accountRegistry.save(account);
    }
}