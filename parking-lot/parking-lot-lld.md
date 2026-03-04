# 🅿️ Parking Lot LLD — Complete Interview Guide

---

## 📁 Final Package Structure

```
org.example
├── Main.java                          ← Demo / entry point
├── model/
│   ├── enums/
│   │   ├── VehicleType.java           BIKE, CAR, TRUCK
│   │   ├── SpotType.java              SMALL, MEDIUM, LARGE, EV, HANDICAPPED
│   │   ├── ParkingSpotStatus.java     AVAILABLE, OCCUPIED, RESERVED, OUT_OF_SERVICE
│   │   └── PaymentStatus.java         PENDING, PAID, FAILED
│   ├── Vehicle.java                   abstract base
│   ├── Bike / Car / Truck.java        concrete vehicles
│   ├── ParkingSpot.java               thread-safe with ReentrantLock
│   ├── ParkingFloor.java              holds spots, owns DisplayBoard
│   ├── ParkingLot.java                Singleton
│   ├── Ticket.java                    entry record
│   ├── Payment.java                   financial transaction
│   └── DisplayBoard.java              Observer implementation
├── observer/
│   └── ParkingLotObserver.java        interface
├── strategy/
│   ├── ParkingStrategy.java           interface
│   ├── NearestSpotStrategy.java       scan floor 0→N
│   └── RandomSpotStrategy.java        random spot for load testing
├── billing/
│   ├── BillingStrategy.java           interface
│   ├── HourlyBillingStrategy.java     ceil(minutes/60) × rate
│   └── FlatRateBillingStrategy.java   per-day flat rate
├── factory/
│   └── VehicleFactory.java            creates Bike/Car/Truck
├── service/
│   ├── ParkingService.java            orchestrates park/unpark
│   ├── TicketService.java             ticket lifecycle
│   └── PaymentService.java            billing + payment
└── exception/
    ├── ParkingLotFullException.java
    ├── InvalidTicketException.java
    └── PaymentFailedException.java
```

---

## 🎤 Step 1 — Clarifying Questions (say these FIRST in the interview)

| # | Question to Ask | Why It Matters |
|---|-----------------|----------------|
| 1 | What vehicle types? Bike, car, truck only? | Drives `VehicleType` enum |
| 2 | Multi-floor or single floor? | Determines `ParkingFloor` abstraction |
| 3 | What billing model — hourly, flat rate, per minute? | Shapes billing strategy |
| 4 | Any special spots — EV charging, handicapped? | Extends `SpotType` enum |
| 5 | Multiple entry/exit gates (concurrency)? | Drives thread-safety design |
| 6 | Do we need a display board? | Adds Observer pattern |
| 7 | Reservations / pre-booking needed? | Adds `Reservation` entity |

> **Pro tip:** Spend 3–5 minutes on questions. It shows you think before coding.

---

## 🧠 Step 2 — How I Thought About This (mental model)

### Start with the nouns → those become classes
> "A parking lot has floors. Floors have spots. A vehicle parks in a spot. A ticket is issued. Payment is made."

- **Parking Lot** → `ParkingLot`
- **Floor** → `ParkingFloor`
- **Spot** → `ParkingSpot`
- **Vehicle** → `Vehicle` (abstract), `Bike`, `Car`, `Truck`
- **Ticket** → `Ticket`
- **Payment** → `Payment`

### Then find the verbs → those become methods/services
> "Park a vehicle. Unpark a vehicle. Calculate fare. Process payment."

- Park → `ParkingService.parkVehicle()`
- Unpark → `ParkingService.unparkVehicle()`
- Calculate fare → `BillingStrategy.calculateFare()`
- Process payment → `PaymentService.processPayment()`

---

## 🔧 Step 3 — Design Patterns Used (and WHY)

### 1. Singleton — `ParkingLot`
```java
private static volatile ParkingLot instance;

public static ParkingLot getInstance(String name, String address) {
    if (instance == null) {
        synchronized (ParkingLot.class) {
            if (instance == null) {
                instance = new ParkingLot(name, address);
            }
        }
    }
    return instance;
}
```
**Why:** There is exactly ONE parking lot. Double-checked locking with `volatile` ensures thread-safety.
**Trade-off to mention:** Global state is hard to test → in production, prefer DI (pass `ParkingLot` as a constructor parameter).

---

### 2. Strategy — `ParkingStrategy` + `BillingStrategy`
```java
// Swap at runtime — zero changes to ParkingService
ParkingService weekday = new ParkingService(lot, new NearestSpotStrategy(), ...);
ParkingService testing = new ParkingService(lot, new RandomSpotStrategy(), ...);
ParkingService airport = new ParkingService(lot, ..., new FlatRateBillingStrategy());
```
**Why:** Different lots charge differently. Different strategies for spot selection. Open/Closed Principle — add new strategy without modifying existing code.

---

### 3. Observer — `ParkingLotObserver` + `DisplayBoard`
```java
// ParkingSpot notifies all observers when status changes
observers.forEach(o -> o.onSpotStatusChanged(this));
```
**Why:** DisplayBoard needs to react when a spot is taken/freed. Tomorrow you can add an SMS notifier, a logger, an LED panel — just implement the interface and register. Zero changes to `ParkingSpot`.

---

### 4. Factory — `VehicleFactory`
```java
Vehicle car = VehicleFactory.create(VehicleType.CAR, "KA-01-AB-1234");
```
**Why:** Encapsulates subclass creation. If `ElectricCar` is added tomorrow, only `VehicleFactory` changes.

---

### 5. Abstract Class — `Vehicle`
**Why abstract class, not interface?**
A `Vehicle` always HAS shared state (`licensePlate`, `vehicleType`) — not just a contract. Abstract class for shared state + behavior; interface for pure contracts.

---

## ⚙️ Step 4 — Key Algorithms

### Spot Assignment — Nearest Spot Strategy
```
For each floor (0 → N):
  For each spot on that floor:
    If spot.status == AVAILABLE AND spotType.canFit(vehicleType):
      return spot   ← first match = nearest to entrance
Return null         ← no spot = throw ParkingLotFullException
```

### Spot Fit Rules (`SpotType.canFit`)
```
BIKE  → fits in SMALL, MEDIUM, LARGE
CAR   → fits in MEDIUM, LARGE, EV, HANDICAPPED
TRUCK → fits in LARGE only
```

### Billing — Hourly Strategy
```
totalMinutes = exitTime - entryTime (in minutes)
billableHours = max(1, ceil(totalMinutes / 60))   ← minimum 1 hour, always round UP
fare = billableHours × ratePerHour[vehicleType]
```
**Edge case:** Uses `ChronoUnit.MINUTES` between two `LocalDateTime` → handles midnight crossings correctly.

---

## 🔒 Step 5 — Concurrency (impress the interviewer)

### Per-spot `ReentrantLock` (not `synchronized(this)`)
```java
private final ReentrantLock lock = new ReentrantLock();

public boolean assignVehicle(Vehicle vehicle) {
    lock.lock();
    try {
        if (status != AVAILABLE) return false;   // another thread got here first
        // assign...
        return true;
    } finally {
        lock.unlock();
    }
}
```
**Why per-spot lock?** Two threads parking on *different* spots proceed in parallel. A global lock would serialize all assignments — terrible throughput at a 500-spot lot.

### Retry Loop in `ParkingService`
```java
while (retries-- > 0) {
    spot = strategy.findSpot(...);      // find a candidate
    if (spot.assignVehicle(vehicle)) break;  // claim it atomically
    spot = null;  // another thread claimed it — retry
}
```
Handles the TOCTOU (time-of-check-time-of-use) race condition.

### `ConcurrentHashMap` in `ParkingFloor`
Multiple gate threads can query spot availability without locking the entire floor.

---

## 🚨 Step 6 — Edge Cases (they WILL ask these)

| Edge Case | How It's Handled |
|-----------|-----------------|
| Lot is full | `ParkingLotFullException` thrown at entry gate |
| Ticket already used | `InvalidTicketException` in `TicketService.validateAndGet()` |
| Payment fails | Spot stays `OCCUPIED`, payment status → `FAILED`, vehicle cannot exit |
| Vehicle type doesn't fit spot | `SpotType.canFit()` enforced; `IllegalArgumentException` if violated |
| Concurrent entry at multiple gates | `ReentrantLock` per spot + retry loop in `ParkingService` |
| Midnight crossing (11pm → 1am) | `ChronoUnit.MINUTES.between(entry, exit)` handles it correctly |
| Null/blank license plate | Validated in `Vehicle` constructor and `VehicleFactory` |
| Spot goes out of service | `ParkingSpotStatus.OUT_OF_SERVICE` — strategy skips these spots |

---

## 🗣️ Step 7 — How to Present This in an Interview

**Opening (1 min):**
> "Before I start designing, let me ask a few clarifying questions..."
*(Ask the 7 questions above)*

**High-level design (2 min):**
> "The core entities are ParkingLot, ParkingFloor, ParkingSpot, Vehicle, Ticket, and Payment. I'll separate concerns into model, service, strategy, billing, and observer packages."

**Design patterns (2 min):**
> "I'll use Singleton for ParkingLot, Strategy for spot selection and billing so they're swappable, Observer so the DisplayBoard auto-updates, and Factory to create vehicles."

**Concurrency (1 min):**
> "Since we have multiple entry gates, I'll use a ReentrantLock per spot for fine-grained locking instead of a global lock, plus a retry loop for the TOCTOU race."

**Code walkthrough (5–7 min):**
> Walk through `ParkingService.parkVehicle()` → `ParkingStrategy.findSpot()` → `ParkingSpot.assignVehicle()` → `TicketService.generateTicket()`

**Edge cases (2 min):**
> Mention lot full, invalid ticket, failed payment, concurrency.

---

## 💡 Follow-up Questions They Might Ask

| Question | Your Answer |
|----------|------------|
| "How would you add EV charging?" | Add `EVChargingSpot extends ParkingSpot` with `chargeVehicle()`. `SpotType.EV` already exists. Zero strategy code changes. |
| "How would you scale to 1000 gates?" | Replace in-memory `ConcurrentHashMap` with Redis for distributed spot state. Use a message queue (Kafka) for entry/exit events. |
| "How would you add reservations?" | Add `Reservation` entity with `vehicleId`, `spotId`, `startTime`, `endTime`. Add `RESERVED` status to `ParkingSpotStatus`. Strategy skips reserved spots unless vehicle matches. |
| "Why not use `synchronized` instead of `ReentrantLock`?" | `ReentrantLock` supports `tryLock()` with timeout, which lets the retry loop fail fast instead of blocking indefinitely. |
| "What if a vehicle never exits?" | Add a background job that flags spots occupied > 24h for manual review. Or add a timeout on tickets. |
