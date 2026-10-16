package com.commitcrew.thebakeway.application.authorization;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.commitcrew.thebakeway.domain.account.Role;

@Component
public class PermissionPolicy {

    private static final Set<String> PROFILE_OPERATIONS = Set.of(
            "VIEW_PROFILE",
            "UPDATE_PROFILE");

    private static final Map<Role, Set<String>> ALLOWED_OPERATIONS = Map.of(
            Role.CUSTOMER, withProfileOperations(
                    "BROWSE_MENU",
                    "PLACE_ORDER",
                    "CANCEL_ORDER",
                    "VIEW_ORDER_HISTORY"),
            Role.RESTAURANT, withProfileOperations(
                    "MANAGE_MENU_ITEMS",
                    "MANAGE_ITEM_AVAILABILITY",
                    "MANAGE_ORDERS",
                    "PREPARE_ORDERS",
                    "ASSIGN_READY_ORDERS_TO_COURIERS"),
            Role.COURIER, withProfileOperations(
                    "VIEW_ASSIGNED_DELIVERIES",
                    "CONFIRM_PICKUP",
                    "CONFIRM_DELIVERY"),
            Role.OPERATOR, withProfileOperations(
                    "VIEW_SIMULATOR_DASHBOARD",
                    "CANCEL_ORDER"));

    public boolean isAllowed(String operation, Role role) {
        if (operation == null || role == null) {
            return false;
        }

        return ALLOWED_OPERATIONS.getOrDefault(role, Set.of()).contains(operation);
    }

    private static Set<String> withProfileOperations(String... roleOperations) {
        Set<String> operations = new HashSet<>(PROFILE_OPERATIONS);
        operations.addAll(Set.of(roleOperations));
        return Set.copyOf(operations);
    }
}
