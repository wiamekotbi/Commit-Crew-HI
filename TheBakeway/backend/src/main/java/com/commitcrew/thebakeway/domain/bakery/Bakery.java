package com.commitcrew.thebakeway.domain.bakery;

import com.commitcrew.thebakeway.domain.location.Coordinates;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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

    // Dispatch compares this fixed bakery location with configured courier
    // locations. The address remains available for display and Google Maps.
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "latitude", column = @Column(name = "latitude")),
            @AttributeOverride(name = "longitude", column = @Column(name = "longitude"))
    })
    private Coordinates coordinates;

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

    /**
     * Creates a bakery with the location needed by courier dispatch. The
     * original constructor is retained temporarily for existing callers while
     * the city configuration is migrated to supply coordinates.
     */
    public Bakery(
            String bakeryId,
            String bakeryName,
            BakeryStatus bakeryStatus,
            String bakeryAddress,
            Coordinates coordinates) {
        this(bakeryId, bakeryName, bakeryStatus, bakeryAddress);

        if (coordinates == null) {
            throw new IllegalArgumentException("Bakery coordinates cannot be null.");
        }
        this.coordinates = coordinates;
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

    public Coordinates getCoordinates() {
        return coordinates;
    }

    /**
     * Pauses an open bakery so it stops accepting new orders. Repeating the
     * command is idempotent, while a permanently closed bakery cannot enter
     * this temporary operational state.
     *
     * @return true only when this call changed the bakery's state
     */
    public boolean pause() {
        if (bakeryStatus == BakeryStatus.paused) {
            return false;
        }
        if (bakeryStatus != BakeryStatus.open) {
            throw new IllegalStateException(
                    "Only an open bakery can be paused");
        }

        bakeryStatus = BakeryStatus.paused;
        return true;
    }

    /**
     * Reopens a temporarily paused bakery. An already-open bakery is an
     * idempotent request. The report treats closed as permanent for this use
     * case, so closed bakeries cannot be reopened through this operation.
     *
     * @return true only when this call changed the bakery's state
     */
    public boolean reopen() {
        if (bakeryStatus == BakeryStatus.open) {
            return false;
        }
        if (bakeryStatus != BakeryStatus.paused) {
            throw new IllegalStateException(
                    "Only a paused bakery can be reopened");
        }

        bakeryStatus = BakeryStatus.open;
        return true;
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
