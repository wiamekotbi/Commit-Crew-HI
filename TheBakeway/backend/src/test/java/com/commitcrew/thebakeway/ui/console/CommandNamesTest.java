package com.commitcrew.thebakeway.ui.console;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CommandNamesTest {

    @ParameterizedTest
    @CsvSource({
            "POST,/api/register,REGISTER",
            "POST,/api/login,LOGIN",
            "POST,/api/logout,LOGOUT",
            "GET,/api/profiles/acc-1,VIEW_PROFILE",
            "PUT,/api/profiles/acc-1,UPDATE_PROFILE",
            "GET,/api/bakeries,BROWSE_BAKERIES",
            "GET,/api/bakeries/bakery-1/menu,BROWSE_MENU",
            "PATCH,/api/bakeries/bakery-1/pause,PAUSE_BAKERY",
            "GET,/api/menu-items,VIEW_OWN_MENU",
            "PATCH,/api/menu-items/item-1/availability,CHANGE_ITEM_AVAILABILITY",
            "POST,/api/orders,PLACE_ORDER",
            "PATCH,/api/orders/o-1/accept,ACCEPT_ORDER",
            "PATCH,/api/orders/o-1/reject,REJECT_ORDER",
            "PATCH,/api/orders/o-1/start-preparing,START_PREPARING",
            "PATCH,/api/orders/o-1/ready,MARK_READY",
            "PATCH,/api/orders/o-1/pickup,CONFIRM_PICKUP",
            "PATCH,/api/orders/o-1/deliver,CONFIRM_DELIVERY",
            "PATCH,/api/orders/o-1/cancel,CANCEL_ORDER",
            "GET,/api/orders/mine,VIEW_MY_ORDERS",
            "GET,/api/orders/bakery,VIEW_BAKERY_ORDERS",
            "GET,/api/orders,VIEW_ALL_ORDERS",
            "GET,/api/orders/o-1,VIEW_ORDER",
            "POST,/api/initial-state/restore,RESTORE_INITIAL_STATE",
            "DELETE,/api/orders/o-1,DELETE /api/orders/o-1",
    })
    void namesEachCommand(String method, String path, String expected) {
        assertEquals(expected, CommandNames.of(method, path));
    }
}
