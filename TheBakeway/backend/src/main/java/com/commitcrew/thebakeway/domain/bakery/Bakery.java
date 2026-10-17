package com.commitcrew.thebakeway.domain.bakery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "bakeries")
public class Bakery {
    
    // Attributes of the Bakery class
    @Id
    @Column(name = "bakery_id", nullable = false, updatable = false)
    private String bakeryId;

    @Column(nullable = false)
    private String bakeryName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BakeryStatus bakeryStatus;

    @Column(nullable = false)
    private String bakeryAddress;

    // Required by JPA when loading a bakery from the database.
    protected Bakery() {
    }

    // Constructor
    public Bakery(String bakeryId, String bakeryName, BakeryStatus bakeryStatus, String bakeryAddress) {

        validateBakeryName(bakeryName);
        validateBakeryStatus(bakeryStatus);
        validateBakeryAddress(bakeryAddress);
        validateBakeryId(bakeryId);

        this.bakeryId = bakeryId;
        this.bakeryName = bakeryName;
        this.bakeryStatus = bakeryStatus;
        this.bakeryAddress = bakeryAddress;  
    }

    // Getters
    public String getBakeryId() {
        return bakeryId;
    }

    public String getBakeryName() {
        return bakeryName; 
    }

    public BakeryStatus getBakeryStatus() {
        return bakeryStatus;
    }

    public String getBakeryAddress() {
        return bakeryAddress;
    }

    // Bakery attributes validation methods
    private void validateBakeryName(String bakeryName){
        if(bakeryName == null || bakeryName.isBlank()){
            throw new IllegalArgumentException("Bakery name cannot be null or empty.");
        }
    }
    private void validateBakeryStatus(BakeryStatus bakeryStatus){
        if(bakeryStatus == null){
            throw new IllegalArgumentException("Bakery status cannot be null.");
        }
    }
    private void validateBakeryAddress(String bakeryAddress){
        if(bakeryAddress == null || bakeryAddress.isBlank()){
            throw new IllegalArgumentException("Bakery address cannot be null or empty.");
        }
    }
    private void validateBakeryId(String bakeryId){
        if(bakeryId == null || bakeryId.isBlank()){
            throw new IllegalArgumentException("Bakery ID cannot be null or empty.");
        }
    }
    
}
