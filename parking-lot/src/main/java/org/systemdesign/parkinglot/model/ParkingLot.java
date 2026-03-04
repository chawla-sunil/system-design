package org.systemdesign.parkinglot.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.systemdesign.parkinglot.model.enums.VehicleType;

/**
 * Singleton representing the entire parking lot.
 *
 * Interview note: Singleton is appropriate here because there is exactly ONE parking lot
 * being managed. We use the double-checked locking idiom with 'volatile' for thread safety.
 *
 * Alternative: Pass ParkingLot as a dependency (DI) to services — avoids global state.
 * For interviews, mentioning both and choosing Singleton with awareness of its trade-offs is ideal.
 */
public class ParkingLot {

    private static volatile ParkingLot instance;

    private final String name;
    private final String address;
    private final List<ParkingFloor> floors = new ArrayList<>();

    // Private constructor prevents direct instantiation
    private ParkingLot(String name, String address) {
        this.name = name;
        this.address = address;
    }

    /** Thread-safe double-checked locking Singleton. */
    public static ParkingLot getInstance(String name, String address) {
        if (instance == null) {
            synchronized (ParkingLot.class) {
                if (instance == null) {
                    instance = new ParkingLot(name, address);
                }
            }
        }
        return instance;
    }

    /** For testing: reset the singleton. Not used in production. */
    public static void reset() {
        instance = null;
    }

    // ──────────────────────────────────────────────
    //  Floor management
    // ──────────────────────────────────────────────

    public void addFloor(ParkingFloor floor) {
        floors.add(floor);
    }

    public List<ParkingFloor> getFloors() {
        return Collections.unmodifiableList(floors);
    }

    // ──────────────────────────────────────────────
    //  Availability queries
    // ──────────────────────────────────────────────

    public long getTotalAvailableSpots(VehicleType vehicleType) {
        return floors.stream()
                .mapToLong(f -> f.getAvailableSpotCount(vehicleType))
                .sum();
    }

    public boolean isFull(VehicleType vehicleType) {
        return getTotalAvailableSpots(vehicleType) == 0;
    }

    public void displayStatus() {
        System.out.println("\n========================================");
        System.out.println("  " + name + " — " + address);
        System.out.println("========================================");
        floors.forEach(f -> {
            System.out.println("  " + f);
            f.getDisplayBoard().display();
        });
        System.out.println("========================================\n");
    }

    // ──────────────────────────────────────────────
    //  Getters
    // ──────────────────────────────────────────────

    public String getName()    { return name; }
    public String getAddress() { return address; }
}

