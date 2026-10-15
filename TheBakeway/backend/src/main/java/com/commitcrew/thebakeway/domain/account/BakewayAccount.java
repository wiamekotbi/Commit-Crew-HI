package com.commitcrew.thebakeway.domain.account;

public class BakewayAccount {
    private String accountId;
    private String username;
    private String password;
    private Role role;

    public BakewayAccount(String accountId, String username, String password, Role role) {
        this.accountId = accountId;
        this.username = username;
        this.password = password;
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

    public Role getRole() {
        return role;
    }
}