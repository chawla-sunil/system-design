# 🛗 Elevator System LLD — Complete Interview Guide

---

## 📁 Final Package Structure

```
org.systemdesign.elevator
├── Main.java                                  ← Demo / entry point
├── model/
│   ├── enums/
│   │   ├── Direction.java                     UP, DOWN, IDLE
│   │   ├── ElevatorState.java                 IDLE, MOVING_UP, MOVING_DOWN, MAINTENANCE
│   │   ├── DoorState.java                     OPEN, CLOSED, OPENING, CLOSING
│   │   └── RequestType.java                   INTERNAL, EXTERNAL
│   ├── Building.java                          Singleton — holds floors + elevators
│   ├── Elevator.java                          id, currentFloor, state, door, display, stop queues
│   ├── Floor.java                             floorNumber, upButton, downButton (thread-safe)
│   ├── Request.java                           base class — sourceFloor, destination, direction, type
│   ├── ExternalRequest.java                   extends Request — floor button press (UP/DOWN)
│   ├── InternalRequest.java                   extends Request — button press inside elevator
│   ├── Door.java                              state machine: CLOSED → OPENING → OPEN → CLOSING → CLOSED
│   └── Display.java                           implements ElevatorObserver — shows current floor + direction
├── observer/
│   ├── ElevatorObserver.java                  interface: onFloorChanged, onStateChanged, onDoor*
│   └── LoggingObserver.java                   implements ElevatorObserver — console logging
├── strategy/
│   ├── ElevatorSelectionStrategy.java         interface: selectElevator(elevators, request)
│   ├── LookSelectionStrategy.java             prefers same-direction approaching elevator
│   └── ShortestSeekTimeStrategy.java          picks nearest available elevator
├── service/
│   ├── ElevatorController.java                orchestrator (NOT singleton) — dispatches requests
│   └── ElevatorService.java                   per-elevator LOOK algorithm loop (Runnable)
├── factory/
│   └── BuildingFactory.java                   creates Building + Floors + Elevators + Controller
└── exception/
    ├── InvalidFloorException.java
    ├── ElevatorOverweightException.java
    ├── ElevatorMaintenanceException.java
    └── AllElevatorsUnavailableException.java
```

---

## 🎤 Step 1 — Clarifying Questions (say these FIRST in the interview)

| # | Question to Ask | Why It Matters |
|---|-----------------|----------------|
| 1 | How many elevators and floors? | Determines data structure sizes and concurrency needs |
| 2 | Passenger or freight elevators? | Affects weight limits and door timing |
| 3 | What scheduling algorithm? FCFS, LOOK, SSTF? | Core algorithm choice — drives Strategy pattern |
| 4 | Multiple people pressing buttons simultaneously? | Drives thread-safety and concurrent request handling |
| 5 | Do we need a display inside/outside the elevator? | Adds Observer pattern |
| 6 | Is there a weight limit? | Adds overweight validation |
| 7 | Do elevators need maintenance mode? | Adds state management and request rerouting |
| 8 | Any VIP/express elevators (serve only certain floors)? | Extends elevator selection strategy |
| 9 | Do we need emergency stop? | Adds queue clearing and safety state |
| 10 | Is this for a single building or multi-building? | Singleton scope |

> **Pro tip:** Spend 3–5 minutes on questions. It shows you understand the problem BEFORE you code.

---

## 🧠 Step 2 — How I Thought About This (mental model)

### Start with the nouns → those become classes

> "A **building** has **floors** and **elevators**. Each elevator has a **door** and a **display**. People make **requests** — either from a floor (external) or inside an elevator (internal). The system has a **controller** that decides which elevator serves which request."

- **Building** → `Building`
- **Floor** → `Floor`
- **Elevator** → `Elevator`
- **Door** → `Door`
- **Display** → `Display`
- **Request** → `Request` (base), `ExternalRequest`, `InternalRequest`
- **Controller** → `ElevatorController`

### Then find the verbs → those become methods/services

> "Dispatch a request. Select an elevator. Move to a floor. Open/close door. Add a stop. Go into maintenance."

- Dispatch request → `ElevatorController.handleExternalRequest()`
- Select elevator → `ElevatorSelectionStrategy.selectElevator()`
- Move to floor → `ElevatorService.moveToFloor()`
- Open/close door → `Door.open()`, `Door.close()`
- Add stop → `Elevator.addStop()`
- Maintenance → `Elevator.setToMaintenance()`

### Two types of requests — the KEY insight

This is the first thing you should explain in an interview:

```
External Request (floor button):
  → "I'm on floor 5, I want to go UP"
  → We don't know the destination yet
  → The system picks an elevator and sends it to floor 5

Internal Request (elevator button):
  → "I'm inside elevator 2, I want to go to floor 9"
  → We know exactly which elevator and which floor
  → Just add floor 9 to elevator 2's stop queue
```

---

## 🔧 Step 3 — Design Patterns Used (and WHY)

### 1. Singleton — `Building` (NOT the controller)

```java
private static volatile Building instance;

public static Building getInstance(String name, int totalFloors) {
    if (instance == null) {
        synchronized (Building.class) {
            if (instance == null) {
                instance = new Building(name, totalFloors);
            }
        }
    }
    return instance;
}
```

**Why Building is Singleton:** There is exactly ONE building. This is a domain constraint.

**Why ElevatorController is NOT Singleton:** The controller is an orchestrator that takes `Building` and `ElevatorSelectionStrategy` as dependencies. Making it a regular class with constructor injection means:
- **Testable** — pass a mock building and mock strategy in tests
- **Swappable** — create a different controller with a different strategy at runtime
- **No global state** — the factory wires it up, clients don't call `getInstance()`

**This is a common interview trap:** "Why is X a singleton but Y isn't?" Your answer: "Singleton is for domain identity (one building), not for service wiring. Services should use dependency injection."

---

### 2. Strategy — `ElevatorSelectionStrategy`

```java
// Swap scheduling algorithm at runtime — zero changes to ElevatorController
ElevatorController withLook = new ElevatorController(building, new LookSelectionStrategy());
ElevatorController withSSTF = new ElevatorController(building, new ShortestSeekTimeStrategy());
```

**Why:** Different buildings may need different scheduling policies. Open/Closed Principle — add a new strategy (e.g., `DestinationDispatchStrategy`) without modifying `ElevatorController`.

**Two strategies implemented:**

| Strategy | How It Works | Pros | Cons |
|----------|-------------|------|------|
| **LOOK** | Prefers elevator already moving toward the floor in the same direction. Falls back to nearest idle. | Minimizes direction reversals, fair | Slightly longer wait for some |
| **SSTF** | Picks nearest available elevator regardless of direction | Minimizes individual wait time | Can starve distant floors |

---

### 3. Observer — `ElevatorObserver` + `Display` + `LoggingObserver`

```java
// Elevator notifies ALL registered observers on every floor change
private void notifyFloorChanged() {
    observers.forEach(o -> o.onFloorChanged(this));
}

// Display is registered as an observer in Elevator's constructor
this.display = new Display(elevatorId);
this.observers.add(display);

// LoggingObserver is registered by ElevatorController during start()
elevator.addObserver(loggingObserver);
```

**Why TWO separate observers?**
- `Display` — shows floor number + direction arrow (mounted on the elevator)
- `LoggingObserver` — logs state changes, door events to console

This follows **Single Responsibility Principle**: each observer does one thing. Tomorrow you can add a `MetricsObserver` for dashboards or an `AlarmObserver` for overweight alerts — zero changes to `Elevator` or existing observers.

**Interview note:** An interviewer might ask "Why not just put the logging in ElevatorService?" Answer: "ElevatorService runs the LOOK algorithm — that's its one job. Logging is a cross-cutting concern that belongs in its own observer. If I put both in the same class, I violate SRP, and I can't swap the logging strategy without touching the scheduling code."

---

### 4. Factory — `BuildingFactory`

```java
ElevatorController controller = BuildingFactory.createStandardBuilding("TechPark", 10, 3);
```

**Why:** Creating a building involves wiring Building + Floors + Elevators + Display + Controller + Strategy. The factory encapsulates this complexity. The client doesn't need to know the wiring details.

---

### 5. State (lightweight) — `ElevatorState` enum

```
IDLE ──── addStop(floor above) ───→ MOVING_UP
IDLE ──── addStop(floor below) ───→ MOVING_DOWN
MOVING_UP ── no more up stops ───→ MOVING_DOWN (if down stops exist)
MOVING_DOWN ── no more down stops → MOVING_UP (if up stops exist)
ANY ──── setToMaintenance() ─────→ MAINTENANCE
MAINTENANCE ── clearMaintenance() → IDLE
```

**Why enum instead of full State pattern classes?** For an interview, an enum with transition logic in `Elevator.addStop()` and `Elevator.getNextStop()` is simpler and sufficient. Mention that you could extract `IdleState`, `MovingUpState`, `MovingDownState`, `MaintenanceState` classes for a production system with complex transition logic.

---

## ⚙️ Step 4 — Key Algorithms

### The LOOK Algorithm (core of the system)

The LOOK algorithm is borrowed from disk scheduling. An elevator (like a disk arm) serves requests in the current direction, then reverses when there are no more requests ahead.

```
┌──────────────────────────────────────────────────────────────┐
│                    LOOK Algorithm                            │
│                                                              │
│  Elevator at floor 4, moving UP                              │
│  upStops:   {6, 8}       ← sorted ascending                 │
│  downStops: {2, 0}       ← sorted descending                │
│                                                              │
│  Step 1: Move UP → serve floor 6 → serve floor 8            │
│  Step 2: No more up stops → REVERSE → now MOVING_DOWN       │
│  Step 3: Move DOWN → serve floor 2 → serve floor 0          │
│  Step 4: No more down stops → IDLE (or reverse again)       │
│                                                              │
│  Floor:  0   1   2   3   4   5   6   7   8   9              │
│          ◄───────────────●───────────────►                   │
│          ↑ downStops  current    upStops ↑                   │
└──────────────────────────────────────────────────────────────┘
```

### Pseudocode — ElevatorService.run()

```
while running:
    if no stops → state = IDLE, sleep, continue

    nextStop = elevator.getNextStop()
    if nextStop == -1 → sleep, continue

    // Move floor by floor (1 second per floor)
    while currentFloor != nextStop:
        moveOneFloor()           // increment/decrement by 1
        notifyObservers()        // Display + LoggingObserver react

    // Arrived at stop
    removeStop(nextStop)
    openDoor()
    wait(2 seconds)              // passengers enter/exit
    closeDoor()
```

### Elevator Selection — LOOK Strategy Scoring

```
For each available elevator:
  Bucket 1 (BEST):  Same direction AND hasn't passed the floor yet
                     → distance = |currentFloor - requestFloor|
  Bucket 2 (GOOD):  IDLE
                     → distance = |currentFloor - requestFloor|
  Bucket 3 (WORST): Opposite direction / already passed
                     → distance = |currentFloor - requestFloor|

Return: best from Bucket 1, else best from Bucket 2, else best from Bucket 3
```

**Example:**
```
Request: Floor 5, UP
Elevator 1: Floor 3, MOVING_UP   → Bucket 1, distance=2 ← BEST
Elevator 2: Floor 7, MOVING_UP   → Bucket 3, distance=2 (already passed floor 5)
Elevator 3: Floor 8, IDLE        → Bucket 2, distance=3
→ Selected: Elevator 1
```

### Stop Queue Data Structures

```java
// Two TreeSets — one for each direction
TreeSet<Integer> upStops   = new TreeSet<>();                          // {2, 5, 8} → natural order
TreeSet<Integer> downStops = new TreeSet<>(Collections.reverseOrder()); // {7, 3, 1} → reverse order

// Adding a stop: O(log S) where S = number of stops
// Getting next stop: O(log S) using higher()/first()
// Removing a stop: O(log S)
```

**Why TreeSet?**
- Keeps stops sorted → no need to sort on every access
- `higher()` / `first()` give us the next stop in O(log S)
- Automatically deduplicates (pressing floor 5 twice = one stop)

---

## 🔒 Step 5 — Concurrency (impress the interviewer)

### Per-elevator `ReentrantLock` on stop queues

```java
private final ReentrantLock lock = new ReentrantLock();

public void addStop(int floor) {
    lock.lock();
    try {
        if (floor > currentFloor) upStops.add(floor);
        else downStops.add(floor);
        // Wake up if idle...
    } finally {
        lock.unlock();
    }
}
```

**Why per-elevator lock?** Two people pressing buttons in *different* elevators proceed in parallel. A global lock would serialize all stop additions — terrible throughput.

### One thread per elevator

```java
// In ElevatorController.start():
ExecutorService threadPool = Executors.newFixedThreadPool(numElevators);
for (Elevator elevator : building.getElevators()) {
    ElevatorService service = new ElevatorService(elevator);
    threadPool.submit(service);   // each elevator runs independently
}
```

**Why one thread per elevator?** Each elevator has its own LOOK loop that runs independently. This is the natural parallelism — elevators don't wait for each other.

### Thread-safe external request dispatch

```java
// handleExternalRequest is called from multiple threads (floor buttons)
// The strategy.selectElevator() reads elevator state → needs consistent reads
// elevator.addStop() uses ReentrantLock → thread-safe
public void handleExternalRequest(ExternalRequest request) {
    building.getFloor(request.getSourceFloor()).pressButton(request.getDirection());
    Elevator selected = strategy.selectElevator(building.getElevators(), request);
    building.getFloor(request.getSourceFloor()).resetButton(request.getDirection());
    elevatorServices.get(selected.getElevatorId()).addStop(request.getSourceFloor());
}
```

### Graceful shutdown

```java
public void shutdown() {
    elevatorServices.values().forEach(ElevatorService::stop);  // signal each thread
    threadPool.shutdown();
    threadPool.awaitTermination(5, TimeUnit.SECONDS);          // wait for completion
}
```

---

## 🚨 Step 6 — Edge Cases (they WILL ask these)

| Edge Case | How It's Handled |
|-----------|-----------------|
| All elevators in maintenance | `AllElevatorsUnavailableException` thrown — caller can retry or queue |
| Request for invalid floor | `InvalidFloorException` — validated in controller before dispatch |
| Elevator already at requested floor | `addStop()` skips if `floor == currentFloor` |
| Maintenance mode set while moving | `setToMaintenance()` clears all stops + sets state → elevator stops at current floor |
| Concurrent button presses on same floor | `TreeSet` deduplicates → floor added only once |
| Concurrent button presses on different floors | Per-elevator `ReentrantLock` → both proceed safely |
| Overweight | `Elevator.addWeight()` returns false → `ElevatorOverweightException` |
| Door malfunction | `Door.open()/close()` are `synchronized` → can add timeout + force close |
| Emergency stop | Call `setToMaintenance()` on the elevator → clears queue, stops movement |
| Request for current floor going same direction | Elevator is already there → just open door |
| Elevator at top floor gets UP request | `moveOneFloor()` bounds-checks against `maxFloor` |

---

## 🗣️ Step 7 — How to Present This in an Interview

**Opening (2–3 min):**
> "Before I start designing, let me ask a few clarifying questions..."
> *(Ask the 10 questions above)*
> "OK, so I'll design for a single building with N floors and M elevators, passenger use, with the LOOK scheduling algorithm, and thread-safe for concurrent requests."

**High-level design (2 min):**
> "The core entities are Building, Floor, Elevator, Door, Display, and Request (external vs internal). I'll separate concerns into model, service, strategy, observer, and factory packages. The key insight is that there are TWO types of requests — external (floor button) and internal (elevator button) — and they're handled differently."

**Draw the flow (1 min):**
> ```
> Person presses UP on floor 5
>   → ExternalRequest(floor=5, dir=UP)
>   → ElevatorController.handleExternalRequest()
>   → LookSelectionStrategy.selectElevator()  ← picks best elevator
>   → ElevatorService.addStop(5)              ← adds floor 5 to elevator's queue
>   → ElevatorService.run() loop picks it up  ← LOOK algorithm serves it
>   → Elevator arrives at floor 5, opens door
>
> Person enters, presses floor 9
>   → InternalRequest(elevatorId=1, dest=9)
>   → ElevatorController.handleInternalRequest()
>   → ElevatorService.addStop(9)
>   → Elevator continues UP to floor 9
> ```

**Design patterns (2 min):**
> "Singleton for Building (domain identity), Strategy for elevator selection (swappable), Observer for Display + LoggingObserver (decoupled), Factory for wiring. The controller uses constructor injection — not singleton — because it's a service, not a domain entity."

**Concurrency (1 min):**
> "Each elevator runs on its own thread. Stop queues use ReentrantLock for fine-grained locking. This lets multiple elevators operate independently while multiple floor buttons can be pressed simultaneously."

**Code walkthrough (5–7 min):**
> Walk through `ElevatorController.handleExternalRequest()` → `LookSelectionStrategy.selectElevator()` → `Elevator.addStop()` → `ElevatorService.run()` → `moveToFloor()` → `openDoor()`

**Edge cases (2 min):**
> Mention maintenance mode, all elevators busy, concurrent presses, overweight.

---

## 💡 Step 8 — Follow-up Questions They Might Ask

| Question | Your Answer |
|----------|------------|
| "How would you add a VIP/express elevator?" | Add a `Set<Integer> servableFloors` to `Elevator`. The selection strategy filters elevators by whether they can serve the requested floor. VIP elevator only serves floors 8-10 + ground. Zero controller changes. |
| "How would you implement destination dispatch?" | Instead of UP/DOWN buttons on each floor, passengers enter their destination at a keypad. The system assigns them to an elevator BEFORE it arrives. Group passengers going to similar floors into the same elevator. Changes: `ExternalRequest` now includes destination; strategy groups by direction. |
| "How would you scale to 100 floors?" | The LOOK algorithm already handles this — O(1) per floor movement. Add "zones" (low-rise: 0-30, mid-rise: 31-60, high-rise: 61-100) with dedicated elevator banks. Each bank has its own `ElevatorController` with a subset of floors. |
| "What if the building has multiple wings?" | Each wing gets its own `Building` instance (remove singleton). A `Campus` class holds multiple buildings. A `CampusController` routes requests to the correct building's controller. |
| "How would you add energy optimization?" | Track elevator idle time. If idle for > X minutes, park the elevator at a "high demand" floor (lobby, cafeteria floor). Use historical data to predict demand patterns. Add a `ParkingStrategy` interface. |
| "How would you add real-time monitoring?" | `ElevatorObserver` already supports this. Implement a `MetricsObserver` that tracks: average wait time, trips per hour, floors per trip, idle time. Push metrics to a dashboard via websocket. |
| "Why LOOK instead of FCFS?" | FCFS (First Come First Served) causes excessive direction changes and is unfair — an elevator at floor 1 going UP would detour to floor 9 DOWN, making people on floor 3 wait forever. LOOK guarantees no starvation — every floor in the current direction gets served before reversing. |
| "Why not `PriorityQueue` instead of `TreeSet`?" | `TreeSet` supports removal by value in O(log N) — needed when a passenger cancels or we remove a processed stop. `PriorityQueue` only supports O(1) removal from the head, O(N) for arbitrary removal. Also, `TreeSet` deduplicates — pressing floor 5 twice adds it only once. |
| "How would you handle power failure?" | Persist the current state (floor, direction, stops) to disk or a database. On restart, resume from the last known state. The `Elevator` class is already serializable-ready (simple state fields). |
| "Why `ReentrantLock` instead of `synchronized`?" | `ReentrantLock` supports `tryLock()` with timeout (fail fast instead of blocking forever) and `lockInterruptibly()` (supports graceful shutdown). In this design, the simple `lock()/unlock()` suffices, but the flexibility is there for future needs. |
| "Why isn't ElevatorController a Singleton?" | It's an orchestrator with dependencies, not a domain identity. Making it a Singleton prevents testing with mock strategies and couples the creation to the class. Building IS a singleton because "one building" is a domain constraint. Controller is just wiring — use DI. |

---

## 🏗️ Step 9 — Class Diagram

```
                                      ┌────────────────────────┐
┌─────────────────┐     creates       │   ElevatorController   │
│ BuildingFactory  │ ────────────────→ │                        │
└─────────────────┘                   │ - building             │
                                      │ - strategy             │
                                      │ - elevatorServices{}   │
                                      │ - threadPool           │
                                      │                        │
                                      │ + handleExternalReq()  │
                                      │ + handleInternalReq()  │
                                      │ + setMaintenance()     │
                                      │ + start() / shutdown() │
                                      └───────────┬────────────┘
                                                  │ uses
                           ┌──────────────────────┼──────────────────────┐
                           │                      │                      │
                           ▼                      ▼                      ▼
             ┌───────────────────┐  ┌──────────────────────┐  ┌──────────────────┐
             │ ElevatorSelection │  │   ElevatorService    │  │     Building     │
             │    Strategy       │  │   (one per elevator) │  │   (Singleton)    │
             │  «interface»      │  │   implements Runnable│  │                  │
             │                   │  │                      │  │ - floors[]       │
             │ + selectElevator()│  │ - elevator           │  │ - elevators[]    │
             └────────┬──────────┘  │ + run()  (LOOK loop) │  │ + getFloor()     │
                      │             │ + addStop()          │  │ + getElevator()  │
           ┌──────────┼──────────┐  │ + stop()             │  └──────────────────┘
           │                     │  └──────────┬───────────┘           │
           ▼                     ▼             │ owns                  │ has
   ┌──────────────┐  ┌────────────────┐  ┌──────────────┐             │
   │    LOOK      │  │    SSTF        │  │  Elevator    │ ◄───────────┘
   │  Strategy    │  │  Strategy      │  │              │
   └──────────────┘  └────────────────┘  │ - id         │
                                         │ - currentFloor│
                                         │ - state      │
                                         │ - upStops    │
                                         │ - downStops  │
                                         │ - lock       │
                                         │ - observers[]│
                                         │              │
                                         │ + addStop()  │
                                         │ + getNextStop│
                                         │ + moveOneFlr │
                                         │ + addObserver│
                                         └──┬────┬──────┘
                                            │    │
                              has-a ────────┘    │ notifies
                             ┌──────────┐        ▼
                             │   Door   │  ┌─────────────────┐
                             │ - state  │  │ ElevatorObserver │
                             │ + open() │  │  «interface»     │
                             │ + close()│  │ + onFloorChanged │
                             └──────────┘  │ + onStateChanged │
                                           │ + onDoorOpened   │
                                           │ + onDoorClosed   │
                                           └────────┬─────────┘
                                                    │
                                           implemented by
                                    ┌───────────────┼───────────────┐
                                    │               │               │
                                    ▼               ▼               ▼
                             ┌──────────┐   ┌──────────────┐  ┌─────────┐
                             │ Display  │   │ Logging      │  │ Future: │
                             │          │   │ Observer     │  │ Metrics │
                             │ + show() │   │              │  │ Alarm   │
                             └──────────┘   │ + onFloor... │  │ SMS     │
                                            │ + onState... │  └─────────┘
                                            │ + onDoor...  │
                                            └──────────────┘

   ┌──────────────┐         ┌─────────────────┐     ┌────────────────────┐
   │   Request    │ ◄───────│ ExternalRequest  │     │  InternalRequest   │
   │  (base)      │ ◄───────│ - sourceFloor    │     │ - elevatorId       │
   │              │         └─────────────────┘     └────────────────────┘
   │ - sourceFloor│
   │ - destFloor  │         ┌──────────┐
   │ - direction  │         │  Floor   │
   │ - type       │         │ - number │
   │ - timestamp  │         │ - upBtn  │
                            │ - downBtn│
                            └──────────┘
```

---

## 🎯 Step 10 — Complexity Analysis

| Operation | Time | Space |
|-----------|------|-------|
| Add a stop (to TreeSet) | O(log S) | — |
| Get next stop | O(log S) | — |
| Remove a stop | O(log S) | — |
| Move one floor | O(1) | — |
| Select elevator (LOOK) | O(E) | — |
| Select elevator (SSTF) | O(E) | — |
| Handle external request | O(E + log S) | — |
| Handle internal request | O(log S) | — |
| Total space per elevator | — | O(S) for stop sets |
| Total space | — | O(E × S + F) |

Where: **E** = number of elevators, **S** = max stops per elevator, **F** = number of floors.

---

## 🔄 Step 11 — Request Flow Summary

```
┌─────────────────────────────────────────────────────────────────────┐
│                     EXTERNAL REQUEST FLOW                           │
│                                                                     │
│  Person on Floor 5         ElevatorController        Elevator 1     │
│       │                          │                       │          │
│       │──── press UP ──────────→ │                       │          │
│       │                          │── pressButton(UP) ───→ Floor 5   │
│       │                          │── selectElevator() ──→│          │
│       │                          │   (LOOK Strategy)     │          │
│       │                          │◄── Elevator 1 ────────│          │
│       │                          │── resetButton(UP) ──→ Floor 5    │
│       │                          │── addStop(5) ────────→│          │
│       │                          │                       │          │
│       │                          │         ┌─── LOOK ────│          │
│       │                          │         │ loop moves  │          │
│       │                          │         │ floor by    │          │
│       │                          │         │ floor → 5   │          │
│       │                          │         └─────────────│          │
│       │◄── door opens ───────────│◄── Display.show() ────│          │
│       │                          │◄── LoggingObserver ───│          │
│       │                          │                       │          │
│       │──── press floor 9 ──────→│── addStop(9) ────────→│          │
│       │                 (internal request)               │          │
│       │                          │         ┌─── LOOK ────│          │
│       │                          │         │ continues   │          │
│       │                          │         │ UP → 9      │          │
│       │                          │         └─────────────│          │
│       │                          │         door opens    │          │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 🧪 Step 12 — Testing Scenarios (mention in interview)

| Scenario | What to Verify |
|----------|---------------|
| Single request, idle elevator | Elevator moves to floor, opens door, goes idle |
| Multiple requests same direction | LOOK serves all in order without reversing |
| Requests in both directions | LOOK completes current direction then reverses |
| Concurrent external requests | Each gets assigned to optimal elevator |
| Elevator in maintenance | Skipped by selection strategy, requests go to others |
| All elevators busy | Least-loaded or nearest elevator handles it |
| Request at current floor | Door opens immediately, no movement |
| Internal request to current floor | No-op (addStop skips) |
| Shutdown mid-operation | Graceful — current operation completes, threads stop |

---

## 🎓 Step 13 — Design Decisions Explained (for grilling questions)

| Decision | Why This Way | What the Interviewer Expects |
|----------|-------------|------|
| `Building` = Singleton, `ElevatorController` ≠ Singleton | Building is a domain identity (one building). Controller is a service — use DI for testability. | Shows you know WHEN to use Singleton vs DI |
| `Display` implements `ElevatorObserver` | Display reacts to elevator events. It shouldn't be directly called by Elevator — that couples them. | Shows you understand the Observer pattern properly |
| `LoggingObserver` separate from `ElevatorService` | ElevatorService runs the LOOK algorithm. Logging is a cross-cutting concern → separate observer. SRP. | Shows you don't pile responsibilities into one class |
| `ElevatorService` is just `Runnable`, not `ElevatorObserver` | The service is the engine. Observing events is a separate concern. | Shows clean separation of concerns |
| `TreeSet` over `PriorityQueue` | Need O(log N) arbitrary removal + deduplication. PQ only has O(1) poll from head. | Shows you reason about data structure trade-offs |
| `ReentrantLock` over `synchronized` | Supports `tryLock()` with timeout, `lockInterruptibly()` for shutdown. More control. | Shows you know when lock > synchronized |
| `Request` base class with `ExternalRequest` / `InternalRequest` subclasses | They have different data and different handling paths. Inheritance models "is-a" correctly here. | Shows clean modeling of related but distinct concepts |
| Floor button state tracked in `Floor` | Controller presses button on request, resets on dispatch. Models real-world behavior. | Shows no dead code — every class earns its existence |
