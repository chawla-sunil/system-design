# 🎬 Movie Ticket Booking (BookMyShow) LLD — Complete Interview Guide

---

## 📁 Final Package Structure

```
org.systemdesign.movieticketbooking
├── Main.java                                  ← Demo / entry point
├── model/
│   ├── enums/
│   │   ├── City.java                          BANGALORE, MUMBAI, DELHI, CHENNAI, HYDERABAD
│   │   ├── SeatType.java                      SILVER, GOLD, PLATINUM
│   │   ├── SeatStatus.java                    AVAILABLE, TEMPORARILY_LOCKED, BOOKED
│   │   ├── BookingStatus.java                 PENDING, CONFIRMED, CANCELLED, EXPIRED
│   │   └── PaymentStatus.java                 PENDING, COMPLETED, FAILED, REFUNDED
│   ├── Movie.java                             title, duration, genre, language
│   ├── Theatre.java                           name, city, screens
│   ├── Screen.java                            screenNumber, seats
│   ├── Seat.java                              physical seat (row, col, seatType)
│   ├── Show.java                              movie + screen + startTime
│   ├── ShowSeat.java                          seat + status for a SPECIFIC show (thread-safe)
│   ├── Booking.java                           user + show + seats + payment
│   ├── Payment.java                           amount, status
│   └── User.java                              name, email, phone
├── service/
│   ├── MovieService.java                      CRUD for movies
│   ├── TheatreService.java                    CRUD for theatres
│   ├── ShowService.java                       CRUD for shows, get available seats
│   ├── BookingService.java                    ★ THE core service — temp lock + book + cancel
│   ├── PaymentService.java                    process payment
│   └── SeatLockService.java                   ★ temporary lock with expiry (concurrency)
├── strategy/
│   ├── PricingStrategy.java                   interface
│   ├── BasePricingStrategy.java               fixed per seat-type
│   └── PeakHourPricingStrategy.java           multiplier on base price
├── observer/
│   ├── BookingObserver.java                   interface
│   ├── EmailNotificationObserver.java         sends email on booking
│   └── SMSNotificationObserver.java           sends SMS on booking
├── exception/
│   ├── SeatNotAvailableException.java
│   ├── SeatTemporarilyLockedException.java
│   ├── BookingNotFoundException.java
│   └── PaymentFailedException.java
└── controller/
    └── MovieTicketBookingSystem.java          Singleton — facade for the entire system
```

---

## 🎤 Step 1 — Clarifying Questions (say these FIRST in the interview)

| # | Question to Ask | Why It Matters |
|---|-----------------|----------------|
| 1 | Are we designing for a single city or multi-city? | Determines how movies/theatres are organized |
| 2 | What seat types exist? Silver, Gold, Platinum? | Drives `SeatType` enum and pricing strategy |
| 3 | Can multiple users select/lock the same seat simultaneously? | **THE most critical concurrency question** — drives seat locking mechanism |
| 4 | How long is a seat temporarily held before payment? | Seat lock expiry duration (typically 5-10 min) |
| 5 | Do we need to support cancellation and refunds? | Adds cancel flow + `REFUNDED` payment status |
| 6 | Multiple shows per screen per day? | Yes — `Show` entity ties movie + screen + time |
| 7 | Do we need notifications on booking? | Adds Observer pattern for email/SMS |

> **Pro tip:** Spend 3–5 minutes on questions. It shows maturity & prevents wasted effort.

---

## 🧠 Step 2 — How I Thought About This (mental model)

### Start with the nouns → those become classes
> "A **User** browses **Movies** playing in **Theatres** in a **City**. Each theatre has **Screens**. A screen has **Seats**. A **Show** is a movie playing on a screen at a specific time. A show has **ShowSeats** (each seat's status for that show). The user makes a **Booking** for selected seats and completes a **Payment**."

### Key Insight: Seat vs ShowSeat
```
Seat     = Physical seat in a screen (row 3, col 5, GOLD) → DOESN'T CHANGE
ShowSeat = That seat's status for a SPECIFIC show         → CHANGES per show
```
row-3-col-5 can be BOOKED for the 2:00 PM show but AVAILABLE for the 6:00 PM show.
This is the **MOST COMMON MISTAKE** in interviews — people put status on `Seat` directly.

### Then find the verbs → those become methods/services
> "Search movies. View shows. Select seats. Lock seats temporarily. Book seats. Pay. Cancel."

- Search movies → `MovieService.getMoviesByCity()`
- View shows → `ShowService.getShowsForMovie()`
- Get available seats → `ShowService.getAvailableSeats()`
- Lock seats temporarily → `SeatLockService.lockSeats()` ★
- Book seats → `BookingService.bookSeats()` ★
- Pay → `PaymentService.processPayment()`
- Cancel → `BookingService.cancelBooking()`

---

## 🔧 Step 3 — Design Patterns Used (and WHY)

### 1. Singleton — `MovieTicketBookingSystem`
```java
private static volatile MovieTicketBookingSystem instance;

public static MovieTicketBookingSystem getInstance() {
    if (instance == null) {
        synchronized (MovieTicketBookingSystem.class) {
            if (instance == null) {
                instance = new MovieTicketBookingSystem();
            }
        }
    }
    return instance;
}
```
**Why:** Single entry point for the entire system. In production, replace with Spring bean.

---

### 2. Strategy — `PricingStrategy`
```java
// Different pricing for different contexts — swap at runtime
PricingStrategy weekday  = new BasePricingStrategy();
PricingStrategy weekend  = new PeakHourPricingStrategy(weekday, 1.5);
PricingStrategy holiday  = new PeakHourPricingStrategy(weekday, 2.0);
```
**Why:** BookMyShow charges differently based on:
- Seat type (Silver < Gold < Platinum)
- Time (morning < evening)
- Day (weekday < weekend < holiday)

Open/Closed Principle — add `StudentDiscountStrategy` without modifying existing code.

---

### 3. Observer — `BookingObserver`
```java
// When a booking is confirmed, notify all observers
observers.forEach(o -> o.onBookingConfirmed(booking));
// When cancelled
observers.forEach(o -> o.onBookingCancelled(booking));
```
**Why:** Email service, SMS service, push notification service all react to booking events independently. Add new notification channels without touching `BookingService`.

---

### 4. Concurrency Design — Temporary Seat Lock ★★★

This is the **STAR of the interview**. BookMyShow's core challenge:

```
User A selects seats [1,2,3] at 10:00:00
User A is redirected to payment page
User B selects the SAME seats [1,2,3] at 10:00:05
```

**Solution: Temporary Lock with Expiry**
```
1. User A selects seats → SeatLockService.lockSeats(showId, seats, userId, 5 min)
2. Seats become TEMPORARILY_LOCKED for User A
3. User B tries same seats → SeatTemporarilyLockedException ← blocked!
4a. User A pays within 5 min → seats become BOOKED ← happy path
4b. User A doesn't pay → ScheduledExecutorService expires the lock → seats become AVAILABLE again
```

---

## ⚙️ Step 4 — Key Algorithms

### Seat Locking Algorithm (THE critical path)
```
lockSeats(showId, seatIds, userId, lockDurationMinutes):
    show = findShow(showId)
    
    synchronized(show) {                          ← lock at show level for atomicity
        for each seatId in seatIds:
            showSeat = show.getShowSeat(seatId)
            if showSeat.status != AVAILABLE:
                throw SeatNotAvailableException    ← ALL or NOTHING
            
        // All seats are available — lock them all
        for each seatId in seatIds:
            showSeat = show.getShowSeat(seatId)
            showSeat.lock(userId)                  ← status → TEMPORARILY_LOCKED
        
        // Schedule expiry
        scheduler.schedule(() -> unlockSeats(showId, seatIds, userId), 
                          lockDurationMinutes, MINUTES)
    }
```
**Key point:** We check ALL seats first, THEN lock ALL seats. This ensures atomicity — no partial locks.

### Booking Algorithm
```
bookSeats(showId, seatIds, userId):
    show = findShow(showId)
    
    synchronized(show) {
        for each seatId in seatIds:
            showSeat = show.getShowSeat(seatId)
            if showSeat is NOT (TEMPORARILY_LOCKED by this userId):
                throw SeatNotAvailableException
        
        totalAmount = pricingStrategy.calculatePrice(show, showSeats)
        payment = paymentService.processPayment(userId, totalAmount)
        
        if payment.status == COMPLETED:
            for each showSeat: showSeat.book()   ← status → BOOKED
            booking = new Booking(user, show, seats, payment)
            return booking
        else:
            throw PaymentFailedException
    }
```

### Pricing Algorithm — Peak Hour Strategy (Decorator pattern)
```
calculatePrice(show, seats):
    basePrice = basePricingStrategy.calculatePrice(show, seats)
    return basePrice × peakMultiplier
```

---

## 🔒 Step 5 — Concurrency Deep Dive (impress the interviewer)

### Why `synchronized(show)` and not per-seat locks?

**Problem with per-seat locks:**
```
User A wants seats [1, 2, 3]
User B wants seats [2, 3, 4]

Thread A locks seat 1, then tries seat 2
Thread B locks seat 4, then tries seat 2
→ Both succeed on seat 2? Or deadlock if using per-seat locks in different order!
```

**Solution: Synchronize on the Show object**
```java
synchronized (show) {
    // Check ALL seats
    // Lock ALL seats
}
```
- One thread at a time per show
- No deadlocks (single lock granularity)
- Different shows still proceed in parallel (different monitors)
- Acceptable because seat selection is fast (milliseconds)

### Lock Expiry with ScheduledExecutorService
```java
private final ScheduledExecutorService scheduler = 
    Executors.newScheduledThreadPool(4);

scheduler.schedule(() -> {
    synchronized (show) {
        for (ShowSeat seat : lockedSeats) {
            if (seat.isLockedBy(userId)) {  // only expire if still locked by same user
                seat.unlock();               // status → AVAILABLE
            }
        }
    }
}, lockDuration, TimeUnit.MINUTES);
```
**Why check `isLockedBy(userId)`?** The user might have already paid (seat is now BOOKED). Don't accidentally unlock a booked seat!

### ConcurrentHashMap for ShowSeat Storage
```java
// Show.java
private final Map<String, ShowSeat> showSeats = new ConcurrentHashMap<>();
```
Read operations (browsing available seats) don't block writes (booking).

---

## 🚨 Step 6 — Edge Cases (they WILL ask these)

| Edge Case | How It's Handled |
|-----------|-----------------|
| Two users book same seat simultaneously | `synchronized(show)` ensures only one succeeds; other gets `SeatNotAvailableException` |
| User selects seats but never pays | `ScheduledExecutorService` expires the lock after N minutes → seats become AVAILABLE |
| Partial seat selection (some available, some not) | ALL-or-NOTHING check before locking. Either all seats are locked or none. |
| User tries to book seats locked by another user | `SeatTemporarilyLockedException` with message "Seats held by another user, try again later" |
| Payment fails after seats are locked | Seats remain TEMPORARILY_LOCKED → eventually expire. Can also explicitly unlock on payment failure. |
| User cancels booking | `BookingService.cancelBooking()` → seats back to AVAILABLE, payment refunded |
| Show time has passed | `ShowService` validates show hasn't started before allowing booking |
| Same user tries to lock same seats twice | Idempotent check — if already locked by same user, return success |

---

## 🗣️ Step 7 — How to Present This in an Interview

**Opening (2–3 min):**
> "Before I start, let me clarify the requirements..."
> *(Ask the 7 questions above)*

**High-level entities (2 min):**
> "The core entities are Movie, Theatre, Screen, Seat, Show, ShowSeat, Booking, Payment, and User. The KEY insight is separating Seat (physical) from ShowSeat (per-show status). This is what makes BookMyShow tick."

**Design patterns (3 min):**
> "I'll use Singleton for the system entry point, Strategy for dynamic pricing, and Observer for booking notifications."

**★ Concurrency — THE differentiator (5 min):**
> "The hardest problem in BookMyShow is: what happens when two users select the same seat? I solve this with a **temporary seat lock mechanism** — when a user selects seats, they're TEMPORARILY_LOCKED for 5 minutes. If the user pays, they become BOOKED. If not, a scheduled task expires the lock. I synchronize on the Show object for atomicity — all seats are checked and locked together, preventing partial locks."

**Code walkthrough (15–20 min):**
> Walk through the booking flow:
> `BookingService.initiateBooking()` → `SeatLockService.lockSeats()` → `ShowSeat.lock()` → 
> User goes to payment page → `BookingService.confirmBooking()` → `PaymentService.processPayment()` → 
> `ShowSeat.book()` → `Booking created` → `Observers notified`

**Edge cases (3 min):**
> Mention concurrent booking, lock expiry, payment failure, cancellation.

---

## 💡 Follow-up Questions They Might Ask

| Question | Your Answer |
|----------|------------|
| "How would you scale to millions of concurrent users?" | Replace `synchronized(show)` with Redis distributed locks (`SETNX` with TTL). ShowSeat state in Redis for distributed reads. Use message queue (Kafka) for booking events. |
| "How would you handle seat selection on a UI?" | WebSocket connection. When a seat is locked, broadcast to all connected clients → seat turns grey in real-time. |
| "Why not use database-level optimistic locking?" | In production, yes! Use version column + `UPDATE ... WHERE version = ?`. If update returns 0 rows → conflict. For LLD interview, in-memory `synchronized` demonstrates the concept. |
| "How would you add discount coupons?" | Add `Coupon` entity and `DiscountStrategy`. Apply coupon in `PricingStrategy` chain. Decorator pattern on top of base pricing. |
| "How would you add waitlist for sold-out shows?" | Add `Waitlist` per show. When a booking is cancelled, notify first user on waitlist. Observer pattern + priority queue. |
| "What if the payment gateway is slow (30 seconds)?" | Don't hold `synchronized(show)` during payment! Lock seats first (quick), release the monitor, then process payment separately. If payment fails, unlock seats explicitly. This is exactly what the two-phase approach (lock → confirm) achieves. |
| "Why ScheduledExecutorService and not a cron job?" | ScheduledExecutorService is per-JVM, fine for LLD. In production, use Redis TTL for lock expiry or a distributed scheduler like Quartz. |

---

## 🏗️ Step 8 — Sequence Diagram (Booking Flow)

```
User                BookingService       SeatLockService       ShowSeat          PaymentService       Observer
 |                       |                     |                   |                   |                  |
 |--- selectSeats() ---->|                     |                   |                   |                  |
 |                       |--- lockSeats() ---->|                   |                   |                  |
 |                       |                     |-- synchronized -->|                   |                  |
 |                       |                     |   check AVAILABLE |                   |                  |
 |                       |                     |   set TEMP_LOCKED |                   |                  |
 |                       |                     |<-- lock acquired -|                   |                  |
 |                       |                     |                   |                   |                  |
 |                       |                     |-- schedule expiry (5 min) ---------->|                  |
 |                       |<-- seats locked ----|                   |                   |                  |
 |<-- show payment page -|                     |                   |                   |                  |
 |                       |                     |                   |                   |                  |
 |--- confirmBooking() ->|                     |                   |                   |                  |
 |                       |--- processPayment() ---------------------------------->|                  |
 |                       |<-- payment success  -----------------------------------|                  |
 |                       |                     |                   |                   |                  |
 |                       |------------- set BOOKED --------------->|                   |                  |
 |                       |                     |                   |                   |                  |
 |                       |--- notifyObservers() --------------------------------------------------->|
 |                       |                     |                   |                   |          email/SMS
 |<-- booking confirmed -|                     |                   |                   |                  |
```

