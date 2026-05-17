package org.systemdesign.wms.service;

import org.systemdesign.wms.model.Order;
import org.systemdesign.wms.model.OrderItem;
import org.systemdesign.wms.model.enums.OrderStatus;
import org.systemdesign.wms.model.enums.OrderType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages order lifecycle — creation, state transitions, and coordination with InventoryService.
 */
public class OrderService {

    private final Map<String, Order> orders;
    private final InventoryService inventoryService;

    public OrderService(InventoryService inventoryService) {
        this.orders = new ConcurrentHashMap<>();
        this.inventoryService = inventoryService;
    }

    /**
     * Create a new order (inbound or outbound).
     */
    public Order createOrder(OrderType type, List<OrderItem> items) {
        Order order = new Order(type, items);
        orders.put(order.getOrderId(), order);
        System.out.println("📝 Created " + type + " order: " + order.getOrderId()
                + " with " + items.size() + " line item(s)");
        return order;
    }

    /**
     * Process an INBOUND order — receive all items into the warehouse.
     * Transitions: CREATED → RECEIVING → RECEIVED → COMPLETED
     */
    public void processInboundOrder(Order order) {
        if (order.getOrderType() != OrderType.INBOUND) {
            throw new RuntimeException("Not an inbound order: " + order.getOrderId());
        }

        // CREATED → RECEIVING
        order.transitionTo(OrderStatus.RECEIVING);
        System.out.println("🔄 Order " + order.getOrderId() + " → RECEIVING");

        // Receive each item
        for (OrderItem item : order.getItems()) {
            inventoryService.receiveStock(item.getProduct(), item.getQuantity());
        }

        // RECEIVING → RECEIVED
        order.transitionTo(OrderStatus.RECEIVED);
        System.out.println("✅ Order " + order.getOrderId() + " → RECEIVED");

        // RECEIVED → COMPLETED
        order.transitionTo(OrderStatus.COMPLETED);
        System.out.println("✅ Order " + order.getOrderId() + " → COMPLETED");
    }

    /**
     * Process an OUTBOUND order through the full lifecycle.
     * Transitions: CREATED → PICKING → PACKED → SHIPPED
     */
    public void processOutboundOrder(Order order) {
        if (order.getOrderType() != OrderType.OUTBOUND) {
            throw new RuntimeException("Not an outbound order: " + order.getOrderId());
        }

        // CREATED → PICKING
        order.transitionTo(OrderStatus.PICKING);
        System.out.println("🔄 Order " + order.getOrderId() + " → PICKING");

        // Pick items — deduct from inventory
        for (OrderItem item : order.getItems()) {
            inventoryService.deductStock(item.getProduct(), item.getQuantity());
        }

        // PICKING → PACKED
        order.transitionTo(OrderStatus.PACKED);
        System.out.println("📦 Order " + order.getOrderId() + " → PACKED");
    }

    /**
     * Ship a packed order.
     */
    public void shipOrder(Order order) {
        order.transitionTo(OrderStatus.SHIPPED);
        System.out.println("🚚 Order " + order.getOrderId() + " → SHIPPED");
    }

    /**
     * Mark as delivered.
     */
    public void markDelivered(Order order) {
        order.transitionTo(OrderStatus.DELIVERED);
        System.out.println("✅ Order " + order.getOrderId() + " → DELIVERED");
    }

    /**
     * Cancel an order (only from CREATED or PICKING/RECEIVING states).
     * If items were already picked, they are restocked.
     */
    public void cancelOrder(Order order) {
        OrderStatus previousStatus = order.getStatus();
        order.transitionTo(OrderStatus.CANCELLED);
        System.out.println("❌ Order " + order.getOrderId() + " → CANCELLED");

        // If outbound items were already picked, restock them
        if (order.getOrderType() == OrderType.OUTBOUND
                && previousStatus == OrderStatus.PICKING) {
            System.out.println("   ↩️ Restocking picked items...");
            for (OrderItem item : order.getItems()) {
                inventoryService.receiveStock(item.getProduct(), item.getQuantity());
            }
        }
    }

    /**
     * Get an order by ID.
     */
    public Order getOrder(String orderId) {
        return orders.get(orderId);
    }

    /**
     * Get all orders filtered by type and/or status.
     */
    public List<Order> getOrders(OrderType type, OrderStatus status) {
        return orders.values().stream()
                .filter(o -> (type == null || o.getOrderType() == type))
                .filter(o -> (status == null || o.getStatus() == status))
                .collect(Collectors.toList());
    }

    /**
     * Print all orders.
     */
    public void printAllOrders() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("📋 ALL ORDERS");
        System.out.println("=".repeat(60));
        for (Order order : orders.values()) {
            System.out.println("  " + order);
            for (OrderItem item : order.getItems()) {
                System.out.println("      └─ " + item);
            }
        }
        System.out.println("=".repeat(60));
    }
}

