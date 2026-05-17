package org.systemdesign.wms.strategy;

import org.systemdesign.wms.model.Product;
import org.systemdesign.wms.model.StorageLocation;

import java.util.List;

/**
 * Strategy Pattern — defines how to pick the best storage location
 * for placing incoming stock.
 */
public interface StorageAllocationStrategy {

    /**
     * Select the best storage location for placing the given product & quantity.
     *
     * @param product   the product to place
     * @param quantity  how many units to place
     * @param locations all available locations
     * @return the selected location, or null if none is suitable
     */
    StorageLocation allocate(Product product, int quantity, List<StorageLocation> locations);
}

