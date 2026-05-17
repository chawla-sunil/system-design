package org.systemdesign.wms.strategy;

import org.systemdesign.wms.model.Product;
import org.systemdesign.wms.model.StorageLocation;

import java.util.Comparator;
import java.util.List;

/**
 * Balanced Load Strategy — picks the location with the MOST available capacity
 * to distribute stock evenly across the warehouse.
 */
public class BalancedLoadStrategy implements StorageAllocationStrategy {

    @Override
    public StorageLocation allocate(Product product, int quantity, List<StorageLocation> locations) {
        double volumeNeeded = product.getVolume() * quantity;
        return locations.stream()
                .filter(loc -> loc.getAvailableCapacity() >= volumeNeeded)
                .max(Comparator.comparingDouble(StorageLocation::getAvailableCapacity))
                .orElse(null);
    }
}

