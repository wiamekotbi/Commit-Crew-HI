package com.commitcrew.thebakeway.application.authorization;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.commitcrew.thebakeway.domain.account.Role;

class PermissionPolicyTest {

    private static final List<String> ALL_OPERATIONS = List.of(
            "VIEW_PROFILE",
            "UPDATE_PROFILE",
            "BROWSE_MENU",
            "PLACE_ORDER",
            "VIEW_ORDER_HISTORY",
            "MANAGE_MENU_ITEMS",
            "MANAGE_ITEM_AVAILABILITY",
            "MANAGE_ORDERS",
            "PREPARE_ORDERS",
            "ASSIGN_READY_ORDERS_TO_COURIERS",
            "VIEW_ASSIGNED_DELIVERIES",
            "CONFIRM_PICKUP",
            "CONFIRM_DELIVERY",
            "VIEW_SIMULATOR_DASHBOARD");

    private static final Set<String> PROFILE_OPERATIONS = Set.of(
            "VIEW_PROFILE",
            "UPDATE_PROFILE");

    private final PermissionPolicy permissionPolicy = new PermissionPolicy();

    @Test
    void customerHasOnlyCustomerPermissions() {
        assertAllowedAndDenied(Role.CUSTOMER, withProfileOperations(
                "BROWSE_MENU",
                "PLACE_ORDER",
                "VIEW_ORDER_HISTORY"));
    }

    @Test
    void restaurantHasOnlyRestaurantPermissions() {
        assertAllowedAndDenied(Role.RESTAURANT, withProfileOperations(
                "MANAGE_MENU_ITEMS",
                "MANAGE_ITEM_AVAILABILITY",
                "MANAGE_ORDERS",
                "PREPARE_ORDERS",
                "ASSIGN_READY_ORDERS_TO_COURIERS"));
    }

    @Test
    void courierHasOnlyCourierPermissions() {
        assertAllowedAndDenied(Role.COURIER, withProfileOperations(
                "VIEW_ASSIGNED_DELIVERIES",
                "CONFIRM_PICKUP",
                "CONFIRM_DELIVERY"));
    }

    @Test
    void operatorHasOnlyOperatorPermissions() {
        assertAllowedAndDenied(Role.OPERATOR, withProfileOperations(
                "VIEW_SIMULATOR_DASHBOARD"));
    }

    @Test
    void rejectsMissingOperationOrRole() {
        assertFalse(permissionPolicy.isAllowed(null, Role.CUSTOMER));
        assertFalse(permissionPolicy.isAllowed("BROWSE_MENU", null));
    }

    private void assertAllowedAndDenied(Role role, Set<String> allowedOperations) {
        for (String operation : ALL_OPERATIONS) {
            if (allowedOperations.contains(operation)) {
                assertTrue(permissionPolicy.isAllowed(operation, role),
                        () -> role + " should be allowed to " + operation);
            } else {
                assertFalse(permissionPolicy.isAllowed(operation, role),
                        () -> role + " should be denied " + operation);
            }
        }
    }

    private Set<String> withProfileOperations(String... roleOperations) {
        Set<String> operations = new HashSet<>(PROFILE_OPERATIONS);
        operations.addAll(Set.of(roleOperations));
        return operations;
    }
}
