package org.systemdesign.wms;

import org.systemdesign.wms.observer.InventoryObserver;
import org.systemdesign.wms.observer.LowStockAlertObserver;
import org.systemdesign.wms.service.InventoryService;
import org.systemdesign.wms.service.OrderService;
import org.systemdesign.wms.service.WarehouseService;
import org.systemdesign.wms.strategy.StorageAllocationStrategy;

/**
 * Singleton — The central Warehouse object.
 * Aggregates all services and provides a single point of access.
 *
 * Thread-safe via double-checked locking.
 */
public class Warehouse {

    private static volatile Warehouse instance;

    private final String name;
    private final WarehouseService warehouseService;
    private final InventoryService inventoryService;
    private final OrderService orderService;

    private Warehouse(String name, StorageAllocationStrategy strategy) {
        this.name = name;
        this.warehouseService = new WarehouseService();
        this.inventoryService = new InventoryService(
                warehouseService.getAllLocations(), strategy);
        this.orderService = new OrderService(inventoryService);

        // Register default observer
        this.inventoryService.addObserver(new LowStockAlertObserver());
    }

    public static Warehouse getInstance(String name, StorageAllocationStrategy strategy) {
        if (instance == null) {
            synchronized (Warehouse.class) {
                if (instance == null) {
                    instance = new Warehouse(name, strategy);
                }
            }
        }
        return instance;
    }

    /**
     * Reset singleton (useful for testing).
     */
    public static void resetInstance() {
        synchronized (Warehouse.class) {
            instance = null;
        }
    }

    // ----- Accessors -----

    public String getName() { return name; }
    public WarehouseService getWarehouseService() { return warehouseService; }
    public InventoryService getInventoryService() { return inventoryService; }
    public OrderService getOrderService() { return orderService; }

    public void addInventoryObserver(InventoryObserver observer) {
        inventoryService.addObserver(observer);
    }
}

