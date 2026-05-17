package org.systemdesign.wms.strategy;

import org.systemdesign.wms.model.Product;
import org.systemdesign.wms.model.StorageLocation;

import java.util.List;

/**
 * Nearest Slot Strategy — picks the first location (by list order)
 * that has enough available capacity.
 * In a real system, "nearest" could mean nearest to the dock or packing station.
 */
public class NearestSlotStrategy implements StorageAllocationStrategy {

    @Override
    public StorageLocation allocate(Product product, int quantity, List<StorageLocation> locations) {
        double volumeNeeded = product.getVolume() * quantity;
        for (StorageLocation loc : locations) {
            if (loc.getAvailableCapacity() >= volumeNeeded) {
                return loc;
            }
        }
        return null; // no suitable location found
    }
}

