package com.commitcrew.thebakeway.domain.order;

import com.commitcrew.thebakeway.domain.menu.MenuItem;
import com.commitcrew.thebakeway.domain.menu.MenuItemStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    @Test
    void shouldCreateOrderSuccessfully() {
        // Act
        Order order = new Order("customer-123", "bakery-123", "Maria", "123 This Street");

        // Assert
        assertEquals("customer-123", order.getCustomerId());
        assertEquals("bakery-123", order.getBakeryId());
        assertEquals("Maria", order.getRecipientName());
        assertEquals("123 This Street", order.getDeliveryAddress());
        assertNull(order.getRejectionReason());
        assertNotNull(order.getCreatedAt());
        assertNotNull(order.getUpdatedAt());
        assertTrue(order.getOrderItems().isEmpty());
    }

    @Test
    void shouldStartInPlacedState() {
        // Act
        Order order = new Order("customer-123", "bakery-123", "Maria", "123 This Street");

        // Assert
        assertEquals("placed", order.getStatus());
    }

    @Test
    void shouldCalculateTotalSuccessfully() {
        // Arrange
        Order order = new Order("customer-123", "bakery-123", "Maria", "123 This Street");
        MenuItem item1 = new MenuItem("bakery-123", "Croissant", "Butter croissant", 3.00, MenuItemStatus.available);
        MenuItem item2 = new MenuItem("bakery-123", "Coffee", "Black coffee", 2.50, MenuItemStatus.available);

        // Act
        order.addOrderItem(item1, 2); // 3.00 * 2 = 6.00
        order.addOrderItem(item2, 1); // 2.50 * 1 = 2.50

        // Assert
        assertEquals(2, order.getOrderItems().size());
        assertEquals(8.50, order.calculateTotal(), 0.001);
    }

    @Test
    void shouldKeepNameAndPriceCopiedAtOrderTime() {
        // Arrange
        Order order = new Order("customer-123", "bakery-123", "Maria", "123 This Street");
        MenuItem croissant = new MenuItem("bakery-123", "Croissant", "Butter croissant", 3.00, MenuItemStatus.available);
        order.addOrderItem(croissant, 2);

        // Act: the bakery edits the menu item after the order was placed
        croissant.setName("Almond Croissant");
        croissant.setPrice(4.25);

        // Assert
        OrderLine line = order.getOrderItems().get(0);
        assertEquals("Croissant", line.getItemName());
        assertEquals(3.00, line.getUnitPrice(), 0.001);
        assertEquals(6.00, line.calculateSubtotal(), 0.001);
        assertEquals(6.00, order.calculateTotal(), 0.001);
    }

    @Test
    void shouldRejectSoldOutItemFromNewOrder() {
        // Arrange: availability represents the current menu state observed
        // while a new order is being assembled.
        Order order = new Order(
                "customer-123", "bakery-123", "Maria", "123 This Street");
        MenuItem soldOutItem = new MenuItem(
                "bakery-123",
                "Croissant",
                "Butter croissant",
                3.00,
                MenuItemStatus.sold_out);

        // Act: sold-out items cannot enter a newly placed order.
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> order.addOrderItem(soldOutItem, 1));

        // Assert: rejection happens before an OrderLine is created, leaving
        // the order unchanged and safe to correct or abandon by the caller.
        assertEquals("Menu item is unavailable for ordering: Croissant", exception.getMessage());
        assertTrue(order.getOrderItems().isEmpty());
        assertEquals(0.00, order.calculateTotal(), 0.001);
    }
}
