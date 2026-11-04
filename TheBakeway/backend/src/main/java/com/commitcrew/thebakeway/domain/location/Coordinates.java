package com.commitcrew.thebakeway.domain.location;

import jakarta.persistence.Embeddable;

/**
 * Geographic coordinates shared by bakeries and couriers. Application code
 * sees an immutable value because no mutators are exposed; the fields remain
 * non-final so JPA can reconstruct the embedded value portably.
 * Embedding this value keeps the latitude and longitude in the owning
 * entity's table instead of creating a separate coordinates table.
 */
@Embeddable
public class Coordinates {
    private double latitude;
    private double longitude;

    /**
     * Required by JPA when reconstructing an embedded value from the database.
     * Application code should use the validated public constructor below.
     */
    protected Coordinates() {
    }

    public Coordinates(double latitude, double longitude) {

        // Validate latitude and longitude values
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) {
            throw new IllegalArgumentException("Latitude and longitude must be finite numbers.");
        }
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90 degrees.");
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180 degrees.");
        }

        // Assign the validated values to the instance variables
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }
}
