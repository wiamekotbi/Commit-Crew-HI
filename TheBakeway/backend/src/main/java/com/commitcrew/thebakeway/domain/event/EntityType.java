package com.commitcrew.thebakeway.domain.event;

/**
 * Identifies the kind of domain object described by a state-transition event.
 * Observers use this value to process order, bakery, and menu-item transitions
 * through one shared event contract.
 */
public enum EntityType {
    ORDER,
    BAKERY,
    MENU_ITEM
}
