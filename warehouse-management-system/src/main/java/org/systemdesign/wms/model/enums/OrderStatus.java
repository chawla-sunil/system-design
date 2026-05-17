package org.systemdesign.wms.model.enums;

public enum OrderStatus {
    // Shared
    CREATED,
    CANCELLED,
    COMPLETED,

    // Inbound-specific
    RECEIVING,
    RECEIVED,

    // Outbound-specific
    PICKING,
    PACKED,
    SHIPPED,
    DELIVERED
}

