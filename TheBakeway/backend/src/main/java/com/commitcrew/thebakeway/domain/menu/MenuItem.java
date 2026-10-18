package com.commitcrew.thebakeway.domain.menu;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;

import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "menu_items")
public class MenuItem {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "menu_item_id", updatable = false, nullable = false)
    private String id;

    @Column(name = "bakery_id", nullable = false)
    private String bakeryId;

    @Column(nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(nullable = false)
    private double price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MenuItemStatus status = MenuItemStatus.available;

    @Column(nullable = false)
    private boolean discontinued = false;

    // Required by JPA
    protected MenuItem() {
    }

    public MenuItem(String bakeryId, String name, String description, double price, MenuItemStatus status) {
        validateBakeryId(bakeryId);
        validateName(name);
        validatePrice(price);
        
        this.bakeryId = bakeryId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.status = (status != null) ? status : MenuItemStatus.available;
        this.discontinued = false; // Default new items to not discontinued
    }

    public String getId() {
        return id;
    }

    public String getBakeryId() {
        return bakeryId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public double getPrice() {
        return price;
    }

    public MenuItemStatus getStatus() {
        return status;
    }

    public boolean isDiscontinued() {
        return discontinued;
    }

    /** Updates the editable menu details without changing ownership or availability. */
    public void updateDetails(String name, String description, double price) {
        validateName(name);
        validatePrice(price);
        this.name = name;
        this.description = description;
        this.price = price;
    }

    // Individual setters remain for compatibility with the existing menu model.
    public void setName(String name) {
        validateName(name);
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setPrice(double price) {
        validatePrice(price);
        this.price = price;
    }

    public void setStatus(MenuItemStatus status) {
        this.status = (status != null) ? status : MenuItemStatus.available;
    }

    public void setDiscontinued(boolean discontinued) {
        this.discontinued = discontinued;
    }

    // Validation helper methods
    private void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Menu item name cannot be null or empty.");
        }
    }

    private void validateBakeryId(String bakeryId) {
        if (bakeryId == null || bakeryId.isBlank()) {
            throw new IllegalArgumentException("Menu item bakery ID cannot be null or empty.");
        }
    }

    private void validatePrice(double price) {
        if (price < 0) {
            throw new IllegalArgumentException("Menu item price cannot be negative.");
        }
    }
}
