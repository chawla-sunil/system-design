package org.systemdesign.wms;

import org.systemdesign.wms.model.Order;
import org.systemdesign.wms.model.OrderItem;
import org.systemdesign.wms.model.Product;
import org.systemdesign.wms.model.StorageLocation;
import org.systemdesign.wms.model.enums.OrderType;
import org.systemdesign.wms.service.InventoryService;
import org.systemdesign.wms.service.OrderService;
import org.systemdesign.wms.service.WarehouseService;
import org.systemdesign.wms.strategy.NearestSlotStrategy;

import java.util.List;

/**
 * Demo driver — simulates a day in the warehouse.
 * Exercises: product registration, storage setup, inbound orders, outbound orders,
 * stock transfers, low-stock alerts, and order lifecycle.
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║   WAREHOUSE MANAGEMENT SYSTEM — LLD Demo               ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝\n");

        // ====================================================================
        // 1. Initialize the Warehouse (Singleton) with Nearest-Slot strategy
        // ====================================================================
        Warehouse warehouse = Warehouse.getInstance("Central Warehouse", new NearestSlotStrategy());
        WarehouseService warehouseService = warehouse.getWarehouseService();
        InventoryService inventoryService = warehouse.getInventoryService();
        OrderService orderService = warehouse.getOrderService();

        // ====================================================================
        // 2. Set up Storage Locations (Rack-Shelf-Bin, capacity in m³)
        // ====================================================================
        System.out.println("--- Setting up storage locations ---");
        StorageLocation locA1 = warehouseService.addLocation("A", "1", "01", 50.0);
        StorageLocation locA2 = warehouseService.addLocation("A", "1", "02", 50.0);
        StorageLocation locB1 = warehouseService.addLocation("B", "1", "01", 100.0);
        StorageLocation locB2 = warehouseService.addLocation("B", "2", "01", 100.0);
        StorageLocation locC1 = warehouseService.addLocation("C", "1", "01", 200.0);

        warehouseService.printWarehouseLayout();

        // ====================================================================
        // 3. Register Products in the catalog
        // ====================================================================
        System.out.println("\n--- Registering products ---");
        Product laptop = warehouseService.addProduct(
                "ELEC-001", "Laptop", "15-inch laptop", 2.5, 0.02, 10);
        Product phone = warehouseService.addProduct(
                "ELEC-002", "Smartphone", "6.5-inch phone", 0.2, 0.005, 20);
        Product headphones = warehouseService.addProduct(
                "ELEC-003", "Headphones", "Wireless headphones", 0.3, 0.003, 15);
        Product tablet = warehouseService.addProduct(
                "ELEC-004", "Tablet", "10-inch tablet", 0.5, 0.01, 5);

        // ====================================================================
        // 4. INBOUND ORDER — Receive stock from supplier
        // ====================================================================
        System.out.println("\n--- Processing Inbound Order #1 (Supplier shipment) ---");
        Order inbound1 = orderService.createOrder(OrderType.INBOUND, List.of(
                new OrderItem(laptop, 50),
                new OrderItem(phone, 200),
                new OrderItem(headphones, 100),
                new OrderItem(tablet, 30)
        ));
        orderService.processInboundOrder(inbound1);

        // Check inventory after inbound
        inventoryService.printInventoryReport(warehouseService.getAllProducts());

        // ====================================================================
        // 5. OUTBOUND ORDER — Customer order fulfillment
        // ====================================================================
        System.out.println("\n--- Processing Outbound Order #1 (Customer order) ---");
        Order outbound1 = orderService.createOrder(OrderType.OUTBOUND, List.of(
                new OrderItem(laptop, 5),
                new OrderItem(phone, 30)
        ));
        orderService.processOutboundOrder(outbound1);
        orderService.shipOrder(outbound1);

        // ====================================================================
        // 6. Another outbound order
        // ====================================================================
        System.out.println("\n--- Processing Outbound Order #2 (Bulk order) ---");
        Order outbound2 = orderService.createOrder(OrderType.OUTBOUND, List.of(
                new OrderItem(laptop, 38),
                new OrderItem(headphones, 90)
        ));
        orderService.processOutboundOrder(outbound2);
        orderService.shipOrder(outbound2);

        // Check inventory — should trigger low-stock alerts
        inventoryService.printInventoryReport(warehouseService.getAllProducts());

        // ====================================================================
        // 7. INTERNAL TRANSFER — Move stock between locations
        // ====================================================================
        System.out.println("\n--- Internal Stock Transfer ---");
        // Find where phone stock is and transfer some to another location
        StorageLocation phoneLocation = inventoryService.getStockByLocation(phone)
                .stream().findFirst().map(item -> item.getLocation()).orElse(null);
        if (phoneLocation != null) {
            inventoryService.transferStock(phone, 50, phoneLocation, locC1);
        }

        // ====================================================================
        // 8. Restock via another inbound order
        // ====================================================================
        System.out.println("\n--- Processing Inbound Order #2 (Restocking low items) ---");
        Order inbound2 = orderService.createOrder(OrderType.INBOUND, List.of(
                new OrderItem(laptop, 100),
                new OrderItem(headphones, 200)
        ));
        orderService.processInboundOrder(inbound2);

        // ====================================================================
        // 9. Cancel an order
        // ====================================================================
        System.out.println("\n--- Creating and cancelling an order ---");
        Order outbound3 = orderService.createOrder(OrderType.OUTBOUND, List.of(
                new OrderItem(tablet, 10)
        ));
        orderService.cancelOrder(outbound3);

        // ====================================================================
        // 10. Final reports
        // ====================================================================
        inventoryService.printInventoryReport(warehouseService.getAllProducts());
        orderService.printAllOrders();
        warehouseService.printWarehouseLayout();

        // ====================================================================
        // 11. Low stock check
        // ====================================================================
        System.out.println("\n--- Low Stock Products ---");
        List<Product> lowStockProducts = inventoryService.getLowStockProducts(
                warehouseService.getAllProducts());
        if (lowStockProducts.isEmpty()) {
            System.out.println("✅ All products are above reorder threshold.");
        } else {
            for (Product p : lowStockProducts) {
                System.out.println("⚠️  " + p.getName() + " (SKU: " + p.getSku()
                        + ") — " + inventoryService.getTotalStock(p) + " units left");
            }
        }

        System.out.println("\n✅ Demo completed successfully!");
    }
}