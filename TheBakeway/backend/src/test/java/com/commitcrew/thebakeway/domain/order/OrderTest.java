package com.commitcrew.thebakeway.domain.order;

import com.commitcrew.thebakeway.domain.menu.MenuItem;
import com.commitcrew.thebakeway.domain.menu.MenuItemStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    @Test
    void shouldCreateOrderSuccessfully() {
        // Act
        Order order = new Order("customer-123", "bakery-123", "123 This Street", OrderStatus.ACCEPTED);

        // Assert
        assertEquals("customer-123", order.getCustomerId());
        assertEquals("bakery-123", order.getBakeryId());
        assertEquals("123 This Street", order.getDeliveryAddress());
        assertEquals(OrderStatus.ACCEPTED, order.getState());
        assertNotNull(order.getCreatedAt());
        assertTrue(order.getOrderItems().isEmpty());
    }

    @Test
    void shouldCalculateTotalSuccessfully() {
        // Arrange
        Order order = new Order("customer-123", "bakery-123", "123 This Street", OrderStatus.ACCEPTED);
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
    void shouldUpdateOrderStateSuccessfully() {
        // Arrange
        Order order = new Order("customer-123", "bakery-123", "123 This Street", OrderStatus.ACCEPTED);

        // Act & Assert
        assertEquals(OrderStatus.ACCEPTED, order.getState());

        order.setState(OrderStatus.PREPARING);
        assertEquals(OrderStatus.PREPARING, order.getState());

        order.setState(OrderStatus.READY);
        assertEquals(OrderStatus.READY, order.getState());
    }
}