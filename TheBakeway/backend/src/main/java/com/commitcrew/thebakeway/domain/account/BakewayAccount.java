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

    /**
     * Identifies the bakery managed by a RESTAURANT account. Other roles leave
     * this column null because they do not own or manage bakery menu data.
     *
     * The association is stored on the authenticated account so authorization
     * never has to trust a bakery ID supplied by an HTTP request.
     */
    @Column(name = "bakery_id")
    private String bakeryId;

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

        // Existing customer/courier/operator construction remains concise.
        // Restaurant accounts must use the overload that includes their bakery.
        this(accountId, username, password, displayName, contactEmail,
                deliveryAddress, role, null);
    }

    /**
     * Creates an account with an optional bakery association. A restaurant
     * account must belong to exactly one bakery, while all other roles must not
     * receive bakery ownership through this constructor.
     */
    public BakewayAccount(
            String accountId,
            String username,
            String password,
            String displayName,
            String contactEmail,
            String deliveryAddress,
            Role role,
            String bakeryId) {

        if (role == null) {
            throw new IllegalArgumentException("Account must have a role");
        }

        if (role == Role.RESTAURANT && (bakeryId == null || bakeryId.isBlank())) {
            throw new IllegalArgumentException("Restaurant account must have a bakery ID");
        }

        if (role != Role.RESTAURANT && (bakeryId != null && !bakeryId.isBlank())) {
            throw new IllegalArgumentException("Only restaurant accounts can have a bakery ID");
        }

        this.accountId = accountId;
        this.username = username;
        this.password = password;
        this.displayName = displayName;
        this.contactEmail = contactEmail;
        this.deliveryAddress = deliveryAddress;
        this.role = role;
        this.bakeryId = bakeryId;
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

    public String getBakeryId() {
        return bakeryId;
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

        // Role and bakery ownership are deliberately excluded from profile
        // updates. They are authorization data established by configuration.
    }
}
