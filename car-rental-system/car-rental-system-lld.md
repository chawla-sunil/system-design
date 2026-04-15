# Car Rental System (Zoomcar) — Low-Level Design Interview

---

## 📌 Step 1: Clarifying Requirements (First 5 minutes)

> **Interviewer:** Design a Car Rental System like Zoomcar.

> **Me:** Sure! Before I jump into the design, let me clarify a few requirements.

### Functional Requirements
1. **User Management** — Users can register, login.
2. **Store/Branch Management** — The system has multiple stores/branches, each in a different location.
3. **Vehicle Management** — Each store has a fleet of vehicles (cars). Vehicles have types (Sedan, SUV, Hatchback).
4. **Search & Browse** — Users can search for available cars at a given store/location for a date range.
5. **Reservation/Booking** — Users can reserve a car for a specific time period.
6. **Payment** — Users pay for the rental (per-hour or per-day pricing).
7. **Return & Invoice** — Users return the car, and the system generates an invoice.
8. **Cancellation** — Users can cancel a reservation.

### Non-Functional (mentioned, not coded in LLD)
- Concurrency handling (double booking prevention)
- Extensibility (new vehicle types, payment methods)

### Out of Scope
- GPS tracking, damage assessment, insurance, customer support tickets.

---

## 📌 Step 2: Identify Core Entities (Next 5 minutes)

| Entity           | Description |
|------------------|-------------|
| `User`           | A registered customer |
| `Store`          | A physical branch/location with cars |
| `Vehicle`        | A car available for rent |
| `VehicleType`    | Category: Sedan, SUV, Hatchback |
| `Reservation`    | A booking made by a user for a vehicle at a store |
| `Invoice`        | Generated after return, contains amount |
| `Payment`        | Payment made for a reservation |
| `Bill`           | Breakdown of charges |

---

## 📌 Step 3: Class Diagram & Relationships (Next 10 minutes)

```
User 1 ---* Reservation
Store 1 ---* Vehicle
Store 1 ---* Reservation
Vehicle *---1 VehicleType
Reservation 1---1 Invoice
Invoice 1---1 Payment
```

### Enums
- `VehicleStatus`: AVAILABLE, RESERVED, RENTED, UNDER_MAINTENANCE
- `ReservationStatus`: SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED
- `PaymentStatus`: PENDING, COMPLETED, FAILED, REFUNDED
- `PaymentMode`: CREDIT_CARD, DEBIT_CARD, UPI, CASH
- `VehicleCategory`: SEDAN, SUV, HATCHBACK

---

## 📌 Step 4: Design Patterns Used

| Pattern       | Where Used |
|---------------|-----------|
| **Strategy**  | Payment processing (different payment modes) |
| **Builder**   | Building Reservation & Invoice objects |
| **Singleton** | VehicleRentalSystem (single system instance) |
| **Factory**   | Creating vehicles of different types |

---

## 📌 Step 5: Detailed Class Design (Next 10 minutes)

### Models
- **User**: id, name, email, phone, drivingLicense
- **Store**: id, name, address, list of vehicles, list of reservations
- **Vehicle**: id, licensePlate, brand, model, year, vehicleType, status, pricePerHour
- **VehicleType**: category (enum), features
- **Reservation**: id, user, vehicle, store, startTime, endTime, status, invoice
- **Invoice**: id, reservation, totalAmount, taxes, discount, bill
- **Payment**: id, invoice, amount, paymentMode, paymentStatus, transactionTime
- **Bill**: baseCost, taxes, totalAmount

### Services
- **UserService**: register, getUser
- **StoreService**: addStore, addVehicle, removeVehicle, getStores
- **VehicleService**: getAvailableVehicles (by store, date range, type)
- **ReservationService**: createReservation, cancelReservation, completeReservation
- **PaymentService**: processPayment (uses Strategy pattern)
- **InvoiceService**: generateInvoice
- **VehicleRentalSystem**: Singleton entry point that wires everything together

---

## 📌 Step 6: Key Algorithms

### Searching Available Vehicles
```
getAvailableVehicles(store, startTime, endTime, category):
    1. Get all vehicles in store
    2. Filter by category (if specified)
    3. Filter by status == AVAILABLE
    4. Check no overlapping reservations for the time range
    5. Return filtered list
```

### Creating a Reservation
```
createReservation(user, store, vehicle, startTime, endTime):
    1. Validate vehicle is available for the time range
    2. Create Reservation object
    3. Mark vehicle as RESERVED
    4. Add reservation to store and user
    5. Return reservation
```

### Completing a Reservation (Return)
```
completeReservation(reservationId):
    1. Find reservation
    2. Set actual return time = now
    3. Calculate total hours
    4. Generate invoice (hours * pricePerHour + taxes)
    5. Mark vehicle as AVAILABLE
    6. Mark reservation as COMPLETED
    7. Return invoice
```

---

## 📌 Step 7: Code Implementation (Next 25 minutes)

> See the Java code in the `src/main/java/org/systemdesign/carrentalsystem/` directory.

### Package Structure
```
org.systemdesign.carrentalsystem/
├── model/
│   ├── User.java
│   ├── Store.java
│   ├── Vehicle.java
│   ├── VehicleType.java
│   ├── Reservation.java
│   ├── Invoice.java
│   ├── Payment.java
│   └── Bill.java
├── enums/
│   ├── VehicleStatus.java
│   ├── VehicleCategory.java
│   ├── ReservationStatus.java
│   ├── PaymentStatus.java
│   └── PaymentMode.java
├── service/
│   ├── UserService.java
│   ├── StoreService.java
│   ├── VehicleService.java
│   ├── ReservationService.java
│   ├── InvoiceService.java
│   └── PaymentService.java
├── strategy/
│   ├── PaymentStrategy.java
│   ├── CreditCardPayment.java
│   ├── UpiPayment.java
│   └── CashPayment.java
├── exception/
│   ├── VehicleNotAvailableException.java
│   ├── ReservationNotFoundException.java
│   └── InvalidPaymentException.java
├── VehicleRentalSystem.java  (Singleton)
└── Main.java (Demo)
```

---

## 📌 Step 8: Edge Cases Discussed (Last 5 minutes)

1. **Double Booking**: Handled by checking overlapping reservations before confirming.
2. **Late Return**: Invoice calculates based on actual return time, not booked end time.
3. **Cancellation**: Only SCHEDULED reservations can be cancelled.
4. **Concurrency**: In production, we'd use optimistic locking or `synchronized` blocks.
5. **Extensibility**: New vehicle types — just add enum. New payment modes — just add a new Strategy.

---

