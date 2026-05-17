# Warehouse Management System (WMS) — Low-Level Design

---

## 🎤 Interview Simulation — How I'd Approach This in a 1-Hour LLD Round

---

### Step 1 — Clarifying Requirements (5 minutes)

> **Me:** Before I jump in, let me clarify the scope. When you say "Warehouse Management System," I want to make sure we're aligned on what features are in scope.

**Questions I'd ask the interviewer:**

1. Are we designing a single warehouse or multi-warehouse system?
2. Do we need to track inventory at the individual item (unit) level, or at a product-quantity (SKU) level?
3. What operations are in scope — Inbound (receiving), Outbound (shipping), internal transfers, returns?
4. Do we need to handle storage location management (racks, shelves, bins)?
5. Do we need order picking & packing workflows?
6. Do we need user roles (Warehouse Manager, Worker, etc.)?
7. Should we handle concurrent access / thread safety?

**Assumed answers for this design:**

- **Single warehouse** with multiple storage locations (racks → shelves → bins).
- **SKU-level inventory** (Product + quantity per location).
- **Inbound** (receive stock), **Outbound** (fulfill orders), and **Internal Transfer** between locations.
- **Order management** with picking and packing workflow.
- **User roles**: Admin, Manager, Worker.
- **Thread safety** for inventory updates (since multiple workers operate concurrently).

---

### Step 2 — Identify Core Entities (5 minutes)

> **Me:** Let me identify the core entities and their relationships.

| Entity | Description |
|---|---|
| `Warehouse` | The physical warehouse. Has multiple storage areas/racks. |
| `StorageLocation` | A specific bin/slot in the warehouse (Rack-Shelf-Bin). |
| `Product` | An SKU — a type of item (e.g., "iPhone 15, 128GB, Black"). |
| `InventoryItem` | Junction: Product + StorageLocation + quantity. |
| `Order` | An inbound or outbound order. |
| `OrderItem` | Line item in an order (product + quantity). |
| `User` | Warehouse staff with roles. |

---

### Step 3 — Use Cases / APIs (5 minutes)

> **Me:** Here are the key use cases:

1. **Add Product** — Register a new SKU in the system.
2. **Receive Stock (Inbound)** — Stock arrives, place it in a storage location.
3. **Create Outbound Order** — Customer order comes in.
4. **Pick & Pack Order** — Worker picks items from locations, packs them.
5. **Ship Order** — Mark order as shipped, deduct inventory.
6. **Internal Transfer** — Move stock between locations.
7. **Check Inventory** — Query stock level for a product across all locations.
8. **Low Stock Alert** — Notify when inventory falls below threshold.

---

### Step 4 — Class Diagram / Design (10 minutes)

> **Me:** Let me draw out the class hierarchy and relationships.

```
                        ┌──────────────┐
                        │  Warehouse   │ (Singleton)
                        │──────────────│
                        │ - locations  │
                        │ - products   │
                        └──────┬───────┘
                               │ 1:N
                    ┌──────────┴──────────┐
                    │   StorageLocation   │
                    │────────────────────│
                    │ - locationId        │
                    │ - rack, shelf, bin  │
                    │ - capacity          │
                    │ - inventoryItems    │
                    └─────────┬──────────┘
                              │ 1:N
                    ┌─────────┴──────────┐
                    │   InventoryItem    │
                    │────────────────────│
                    │ - product          │
                    │ - quantity         │
                    │ - location         │
                    └────────────────────┘

    ┌──────────┐          ┌──────────┐       ┌────────────┐
    │ Product  │          │  Order   │ 1:N   │ OrderItem  │
    │──────────│          │──────────│───────│────────────│
    │ - sku    │          │ - type   │       │ - product  │
    │ - name   │          │ - status │       │ - quantity │
    │ - weight │          │ - items  │       └────────────┘
    └──────────┘          └──────────┘

    Services:
    ┌─────────────────────┐  ┌──────────────────────┐  ┌────────────────────────┐
    │  InventoryService   │  │    OrderService       │  │  WarehouseService      │
    │─────────────────────│  │──────────────────────│  │────────────────────────│
    │ + receiveStock()    │  │ + createOrder()       │  │ + addLocation()        │
    │ + transferStock()   │  │ + pickOrder()         │  │ + findAvailLocation()  │
    │ + getStockLevel()   │  │ + packOrder()         │  │ + addProduct()         │
    │ + deductStock()     │  │ + shipOrder()         │  └────────────────────────┘
    │ + getLowStockItems()│  │ + cancelOrder()       │
    └─────────────────────┘  └──────────────────────┘
```

---

### Step 5 — Design Patterns Used (5 minutes)

> **Me:** Let me call out the design patterns I'll use and why.

| Pattern | Where | Why |
|---|---|---|
| **Singleton** | `Warehouse` | Only one warehouse instance in scope. |
| **Strategy** | `StorageAllocationStrategy` | Different strategies for allocating storage (nearest, balanced, random). |
| **Observer** | `InventoryObserver` / Low-stock alerting | Decouple alerting from inventory logic. |
| **State** | `OrderStatus` transitions | Orders go through well-defined state transitions (CREATED → PICKING → PACKED → SHIPPED). |
| **Factory** | `OrderFactory` | Create different order types (INBOUND/OUTBOUND) cleanly. |

---

### Step 6 — State Machine for Orders

```
INBOUND ORDER:    CREATED → RECEIVING → RECEIVED → COMPLETED
OUTBOUND ORDER:   CREATED → PICKING → PACKED → SHIPPED → DELIVERED
                                                  ↘ CANCELLED (from CREATED/PICKING)
```

---

### Step 7 — Thread Safety Considerations

> **Me:** In a real warehouse, multiple workers pick from the same locations concurrently. I'll use:
> - `synchronized` blocks on `InventoryItem.adjustQuantity()` or `ReentrantLock` per location.
> - `ConcurrentHashMap` for the product and location registries.
> - Optimistic locking concept on inventory updates.

---

### Step 8 — Code Implementation (25 minutes)

> **Me:** Let me code this up now. I'll start bottom-up: enums → models → services → strategy → observer → main demo.

*(See source code in `src/main/java/org/systemdesign/wms/`)*

---

### Step 9 — Extensibility Discussion (5 minutes)

> **Me:** If the interviewer asks "How would you extend this?":

1. **Multi-warehouse**: Make `Warehouse` non-singleton, add a `WarehouseNetwork` manager.
2. **Batch/Lot tracking**: Add `Batch` entity with expiry dates (FIFO for perishables).
3. **Barcode/QR scanning**: Add `ScanEvent` entity that maps to inventory actions.
4. **Returns management**: Add `RETURN` order type with inspection workflow.
5. **Reporting/Analytics**: Add `ReportService` with stock turnover, aging analysis.
6. **Database persistence**: Replace in-memory maps with DAO/Repository pattern.
7. **REST API layer**: Add controllers on top of services.

---

## Package Structure

```
org.systemdesign.wms/
├── model/
│   ├── enums/
│   │   ├── OrderStatus.java
│   │   ├── OrderType.java
│   │   └── UserRole.java
│   ├── Product.java
│   ├── StorageLocation.java
│   ├── InventoryItem.java
│   ├── Order.java
│   ├── OrderItem.java
│   └── User.java
├── service/
│   ├── InventoryService.java
│   ├── OrderService.java
│   └── WarehouseService.java
├── strategy/
│   ├── StorageAllocationStrategy.java
│   ├── NearestSlotStrategy.java
│   └── BalancedLoadStrategy.java
├── observer/
│   ├── InventoryObserver.java
│   └── LowStockAlertObserver.java
├── exception/
│   ├── InsufficientStockException.java
│   ├── LocationFullException.java
│   ├── InvalidOrderStateException.java
│   └── ProductNotFoundException.java
├── Warehouse.java  (Singleton — the central orchestrator)
└── Main.java       (Demo / Driver)
```

