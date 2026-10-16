package com.commitcrew.thebakeway.domain.order;

import com.commitcrew.thebakeway.domain.menu.MenuItem;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "order_lines")
public class OrderLine {

    @EmbeddedId
    private OrderLineId id = new OrderLineId();

    @ManyToOne
    @MapsId("orderId")
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne
    @MapsId("menuItemId")
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    // Copied from the menu item at order time so later menu changes do not alter the order
    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "unit_price", nullable = false)
    private double unitPrice;

    @Column(nullable = false)
    private int quantity; // Maps to the quantity column in order_lines

    // Embedded ID class to handle the composite primary key of order_id and menu_item_id
    @Embeddable
    public static class OrderLineId implements Serializable {
        @Column(name = "order_id")
        private String orderId;

        @Column(name = "menu_item_id")
        private String menuItemId;

        public OrderLineId() {}

        public OrderLineId(String orderId, String menuItemId) {
            this.orderId = orderId;
            this.menuItemId = menuItemId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OrderLineId that = (OrderLineId) o;
            return Objects.equals(orderId, that.orderId) && Objects.equals(menuItemId, that.menuItemId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(orderId, menuItemId);
        }

        public String getOrderId() { return orderId; }
        public String getMenuItemId() { return menuItemId; }
    }

    protected OrderLine() {}

    public OrderLine(Order order, MenuItem menuItem, int quantity) {
        this.order = order;
        this.menuItem = menuItem;
        this.itemName = menuItem.getName();
        this.unitPrice = menuItem.getPrice();
        this.quantity = quantity;
        this.id = new OrderLineId(order.getId(), menuItem.getId());
    }

    public double calculateSubtotal() {
        return unitPrice * quantity;
    }

    // Getters and Setters
    public OrderLineId getId() { return id; }
    public Order getOrder() { return order; }
    public MenuItem getMenuItem() { return menuItem; }
    public String getItemName() { return itemName; }
    public double getUnitPrice() { return unitPrice; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}