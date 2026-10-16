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

    // Required by JPA
    protected MenuItem() {
    }

    public MenuItem(String bakeryId, String name, String description, double price, MenuItemStatus status) {
        this.bakeryId = bakeryId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.status = (status != null) ? status : MenuItemStatus.available;
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

    public void setBakeryId(String bakeryId) {
        this.bakeryId = bakeryId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public void setStatus(MenuItemStatus status) {
        this.status = (status != null) ? status : MenuItemStatus.available;
    }
}