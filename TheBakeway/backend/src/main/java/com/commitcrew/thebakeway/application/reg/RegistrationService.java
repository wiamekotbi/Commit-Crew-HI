package com.commitcrew.thebakeway.application.reg;

import java.util.UUID;

import com.commitcrew.thebakeway.domain.account.AccountRegistry;
import com.commitcrew.thebakeway.domain.account.BakewayAccount;
import com.commitcrew.thebakeway.domain.account.Role;
import org.springframework.stereotype.Service;

@Service
public class RegistrationService {

    private final AccountRegistry accountRegistry;

    public RegistrationService(AccountRegistry accountRegistry) {
        this.accountRegistry = accountRegistry;
    }

    public BakewayAccount registerCustomer(
            String name,
            String username,
            String password,
            String confirmPassword,
            String deliveryAddress) {

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }

        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }

        if (!password.equals(confirmPassword)) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        if (deliveryAddress == null || deliveryAddress.isBlank()) {
            throw new IllegalArgumentException("Delivery address is required");
        }

        if (accountRegistry.usernameExists(username)) {
            throw new IllegalArgumentException("Username already taken");
        }

        String accountId = UUID.randomUUID().toString();

        BakewayAccount account = new BakewayAccount(
                accountId,
                username,
                password,
                name,
                "",
                deliveryAddress,
                Role.CUSTOMER);

        accountRegistry.addAccount(account);

        return account;
    }
}