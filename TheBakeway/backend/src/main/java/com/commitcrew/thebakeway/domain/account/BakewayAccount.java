package com.commitcrew.thebakeway.domain.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "accounts")
public class BakewayAccount {

    @Id
    private String accountId;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String displayName;

    private String contactEmail;

    private String deliveryAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    protected BakewayAccount() {
    }

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

    public void updateProfile(String displayName, String contactEmail, String deliveryAddress) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Display name is required");
        }

        this.displayName = displayName;
        this.contactEmail = contactEmail;

        if (role == Role.CUSTOMER) {
            this.deliveryAddress = deliveryAddress;
        }
    }
}
