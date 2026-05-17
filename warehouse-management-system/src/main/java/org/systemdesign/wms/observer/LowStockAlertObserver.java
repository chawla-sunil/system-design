package org.systemdesign.wms.observer;

import org.systemdesign.wms.model.Product;

/**
 * Concrete observer — prints a warning when stock falls below the reorder threshold.
 */
public class LowStockAlertObserver implements InventoryObserver {

    @Override
    public void onStockLevelChanged(Product product, int totalQuantity) {
        if (totalQuantity <= product.getReorderThreshold()) {
            System.out.println("⚠️  LOW STOCK ALERT: Product '" + product.getName()
                    + "' (SKU: " + product.getSku() + ") has only "
                    + totalQuantity + " units left. Reorder threshold: "
                    + product.getReorderThreshold());
        }
    }
}

