package com.commitcrew.thebakeway.application.ord;

import java.util.Map;

import com.commitcrew.thebakeway.domain.account.Role;

// What was loaded from the config files, reported in the console and to the operator
public record InitialStateSummary(int bakeries, int menuItems, Map<Role, Integer> accountsPerRole) {
}
