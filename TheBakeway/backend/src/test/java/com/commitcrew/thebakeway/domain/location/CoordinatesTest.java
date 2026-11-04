package com.commitcrew.thebakeway.domain.location;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

public class CoordinatesTest {

    @Test
    public void testValidCoordinates() {
        
        // A valid location must preserve both values exactly so the dispatch
        // strategy can use them when comparing courier distances.
        Coordinates coordinates = new Coordinates(37.7749, -122.4194);

        assertEquals(37.7749, coordinates.getLatitude());
        assertEquals(-122.4194, coordinates.getLongitude());
    }

    @Test
    public void testInvalidLatitude() {
        // Latitude is limited to the inclusive geographic range [-90, 90].
        assertThrows(IllegalArgumentException.class, () -> new Coordinates(100.0, -122.4194));
        assertThrows(IllegalArgumentException.class, () -> new Coordinates(-91.0, 0.0));
    }

    @Test
    public void testInvalidLongitude() {
        
        // Longitude is limited to the inclusive geographic range [-180, 180].
        assertThrows(IllegalArgumentException.class, () -> new Coordinates(37.7749, -200.0));
        assertThrows(IllegalArgumentException.class, () -> new Coordinates(0.0, 181.0));
    }

    @Test
    public void testNonFiniteValues() {
        // NaN and infinity do not describe real positions. Each value is tested
        // separately so one invalid argument cannot hide another through
        // short-circuit evaluation in the constructor validation.
        assertThrows(IllegalArgumentException.class, () -> new Coordinates(Double.NaN, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new Coordinates(0.0, Double.NaN));
        
        assertThrows(IllegalArgumentException.class,
                () -> new Coordinates(Double.POSITIVE_INFINITY, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new Coordinates(0.0, Double.NEGATIVE_INFINITY));
    }
}
