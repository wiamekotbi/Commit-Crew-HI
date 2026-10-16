package com.commitcrew.thebakeway.domain.menu;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "menu_items")
public class MenuItem {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private double price;

    @Column(nullable = false)
    private boolean inStock = true;

    @Column(nullable = false)
    private boolean available = true;

    // Required by JPA
    protected MenuItem() {
    }

    // ID is generated automatically
    public MenuItem(String name, double price) {
        this.name = name;
        this.price = price;
        this.inStock = true;
        this.available = true;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getPrice() {
        return price;
    }

    public boolean isInStock() {
        return inStock;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public void setInStock(boolean inStock) {
        this.inStock = inStock;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }
}
