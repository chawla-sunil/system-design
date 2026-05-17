package org.systemdesign.wms.model;

import org.systemdesign.wms.model.enums.OrderStatus;
import org.systemdesign.wms.model.enums.OrderType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Represents an inbound (receiving) or outbound (shipping) order.
 * Uses the State pattern via OrderStatus enum with validated transitions.
 */
public class Order {

    private final String orderId;
    private final OrderType orderType;
    private final List<OrderItem> items;
    private OrderStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Order(OrderType orderType, List<OrderItem> items) {
        this.orderId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.orderType = orderType;
        this.items = new ArrayList<>(items);
        this.status = OrderStatus.CREATED;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public String getOrderId() { return orderId; }
    public OrderType getOrderType() { return orderType; }
    public List<OrderItem> getItems() { return Collections.unmodifiableList(items); }
    public OrderStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    /**
     * Transition the order to a new status with validation.
     * Enforces the state machine:
     *   INBOUND:  CREATED → RECEIVING → RECEIVED → COMPLETED
     *   OUTBOUND: CREATED → PICKING → PACKED → SHIPPED → DELIVERED
     *   CANCELLED can be reached from CREATED or PICKING/RECEIVING.
     */
    public void transitionTo(OrderStatus newStatus) {
        if (!isValidTransition(newStatus)) {
            throw new RuntimeException("Invalid order state transition: "
                    + status + " → " + newStatus + " for " + orderType + " order " + orderId);
        }
        this.status = newStatus;
        this.updatedAt = LocalDateTime.now();
    }

    private boolean isValidTransition(OrderStatus newStatus) {
        if (newStatus == OrderStatus.CANCELLED) {
            return status == OrderStatus.CREATED
                    || status == OrderStatus.PICKING
                    || status == OrderStatus.RECEIVING;
        }

        if (orderType == OrderType.INBOUND) {
            return switch (status) {
                case CREATED -> newStatus == OrderStatus.RECEIVING;
                case RECEIVING -> newStatus == OrderStatus.RECEIVED;
                case RECEIVED -> newStatus == OrderStatus.COMPLETED;
                default -> false;
            };
        } else { // OUTBOUND
            return switch (status) {
                case CREATED -> newStatus == OrderStatus.PICKING;
                case PICKING -> newStatus == OrderStatus.PACKED;
                case PACKED -> newStatus == OrderStatus.SHIPPED;
                case SHIPPED -> newStatus == OrderStatus.DELIVERED;
                default -> false;
            };
        }
    }

    @Override
    public String toString() {
        return "Order{id=" + orderId + ", type=" + orderType
                + ", status=" + status + ", items=" + items.size() + "}";
    }
}

