package com.commitcrew.thebakeway.domain.account;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRegistry {

    private final List<BakewayAccount> accounts = new ArrayList<>();

    public void addAccount(BakewayAccount account) {
        accounts.add(account);
    }

    public boolean usernameExists(String username) {
        for (BakewayAccount account : accounts) {
            if (account.getUsername().equals(username)) {
                return true;
            }
        }

        return false;
    }

    public BakewayAccount findByUsername(String username) {
        for (BakewayAccount account : accounts) {
            if (account.getUsername().equals(username)) {
                return account;
            }
        }

        return null;
    }
}