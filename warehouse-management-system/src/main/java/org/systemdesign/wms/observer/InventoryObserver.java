package org.systemdesign.wms.observer;

import org.systemdesign.wms.model.Product;

/**
 * Observer Pattern — gets notified when inventory levels change.
 */
public interface InventoryObserver {

    /**
     * Called when the total stock level for a product changes.
     *
     * @param product       the product whose stock changed
     * @param totalQuantity the new total quantity across all locations
     */
    void onStockLevelChanged(Product product, int totalQuantity);
}

