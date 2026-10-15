package com.commitcrew.thebakeway.domain.account;

public class BakewayAccount {
    private String accountId;
    private String username;
    private String password;
    private String displayName;
    private String contactEmail;
    private String deliveryAddress;
    private final Role role;

    public BakewayAccount(
            String accountId,
            String username,
            String password,
            String displayName,
            String contactEmail,
            String deliveryAddress,
            Role role) {
        if (role == null) {
            throw new IllegalArgumentException("Account must have a role");
        }

        this.accountId = accountId;
        this.username = username;
        this.password = password;
        this.displayName = displayName;
        this.contactEmail = contactEmail;
        this.deliveryAddress = deliveryAddress;
        this.role = role;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public Role getRole() {
        return role;
    }
}