package com.commitcrew.thebakeway.ui.console;

import java.util.List;
import java.util.regex.Pattern;

// Readable console name for each HTTP command, for example PATCH /api/orders/{id}/accept -> ACCEPT_ORDER
final class CommandNames {

    private record Route(String method, Pattern path, String command) {
    }

    private static final String ID = "[^/]+";

    private static final List<Route> ROUTES = List.of(
            route("POST", "/api/register", "REGISTER"),
            route("POST", "/api/login", "LOGIN"),
            route("POST", "/api/logout", "LOGOUT"),
            route("GET", "/api/session", "VIEW_SESSION"),
            route("GET", "/api/profiles/" + ID, "VIEW_PROFILE"),
            route("PUT", "/api/profiles/" + ID, "UPDATE_PROFILE"),
            route("GET", "/api/bakeries", "BROWSE_BAKERIES"),
            route("GET", "/api/bakeries/all", "VIEW_ALL_BAKERIES"),
            route("GET", "/api/bakeries/" + ID + "/menu", "BROWSE_MENU"),
            route("PATCH", "/api/bakeries/" + ID + "/pause", "PAUSE_BAKERY"),
            route("PATCH", "/api/bakeries/" + ID + "/reopen", "REOPEN_BAKERY"),
            route("GET", "/api/menu-items", "VIEW_OWN_MENU"),
            route("POST", "/api/menu-items", "ADD_MENU_ITEM"),
            route("PUT", "/api/menu-items/" + ID, "EDIT_MENU_ITEM"),
            route("PATCH", "/api/menu-items/" + ID + "/availability", "CHANGE_ITEM_AVAILABILITY"),
            route("POST", "/api/orders", "PLACE_ORDER"),
            route("PATCH", "/api/orders/" + ID + "/accept", "ACCEPT_ORDER"),
            route("PATCH", "/api/orders/" + ID + "/reject", "REJECT_ORDER"),
            route("PATCH", "/api/orders/" + ID + "/start-preparing", "START_PREPARING"),
            route("PATCH", "/api/orders/" + ID + "/ready", "MARK_READY"),
            route("PATCH", "/api/orders/" + ID + "/pickup", "CONFIRM_PICKUP"),
            route("PATCH", "/api/orders/" + ID + "/deliver", "CONFIRM_DELIVERY"),
            route("PATCH", "/api/orders/" + ID + "/cancel", "CANCEL_ORDER"),
            route("GET", "/api/orders/mine", "VIEW_MY_ORDERS"),
            route("GET", "/api/orders/bakery", "VIEW_BAKERY_ORDERS"),
            route("GET", "/api/orders", "VIEW_ALL_ORDERS"),
            route("GET", "/api/orders/" + ID, "VIEW_ORDER"),
            route("POST", "/api/initial-state/restore", "RESTORE_INITIAL_STATE"));

    private CommandNames() {
    }

    static String of(String method, String path) {
        for (Route route : ROUTES) {
            if (route.method().equals(method) && route.path().matcher(path).matches()) {
                return route.command();
            }
        }
        return method + " " + path;
    }

    private static Route route(String method, String path, String command) {
        return new Route(method, Pattern.compile(path), command);
    }
}
