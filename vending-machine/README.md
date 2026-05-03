# Vending Machine LLD (Java)

This module is written as an **LLD interview solution** for the classic Vending Machine problem. The goal is not only to show working Java code, but also to show **how to approach the problem in an interview**, how to break it into entities and behaviors, and how each class participates in the design.

## How to approach this in an interview

A good LLD interview answer should not start with code. Start with **clarification, scope, entities, behavior, and trade-offs**.

### 1) Clarify requirements first

Ask questions like:

- Is the machine **coin-only** or should it support card/UPI as well?
- Is it **single-product per transaction** or multiple products in one purchase?
- Should it return **exact change**?
- Should there be a **cancel/refund** option?
- Do we need **admin operations** like refill products and refill coins?
- Is an **in-memory design** enough, or is persistence required?
- Should we discuss **concurrency** if two users or an admin act at the same time?

### 2) Freeze scope explicitly

For this implementation, the scope is intentionally kept interview-friendly:

- Single vending machine
- Coin-based payment only
- Single product purchase at a time
- Slot-based inventory such as `A1`, `A2`
- Exact change handling
- Cancel and refund support
- Admin APIs to load products and coins
- In-memory data structures

### 3) Identify core entities

Once scope is fixed, identify the main nouns in the system:

- Product
- Slot
- Coin
- VendingMachine
- VendResult
- Machine state
- Change calculation strategy

### 4) Identify user actions / verbs

In interviews, after nouns come the verbs. The core actions are:

- `loadSlot(...)`
- `loadCoins(...)`
- `insertCoin(...)`
- `currentBalance()`
- `purchase(...)`
- `cancelAndRefund()`
- `stock(...)`
- `state()`

These actions naturally help define the public API of the machine.

### 5) Discuss main flows before coding

#### Happy flow
1. Admin loads slots and initial coins.
2. User inserts coins.
3. User selects a slot.
4. Machine validates slot, stock, money, and change availability.
5. Product is dispensed.
6. Change is returned.
7. Machine goes back to idle.

#### Failure flows
- Invalid slot selected
- Product out of stock
- Insufficient funds
- Exact change not possible
- User cancels and gets refund

### 6) Mention trade-offs and extensibility

A strong interview answer also mentions what can be extended later:

- Replace coins-only with `PaymentStrategy`
- Replace enum state with full State Pattern classes
- Persist inventory and transactions in a database
- Add low-stock alerts, audit logs, metrics, and admin dashboards
- Use finer-grained locking instead of `synchronized` for scale

---

## Design assumptions used in this code

This implementation makes the following assumptions:

- Supported coins are `NICKEL`, `DIME`, and `QUARTER`
- Prices are stored in **cents** to avoid floating-point issues
- Each slot holds only **one product type**
- A slot can be refilled only with the **same product** once configured
- During purchase, exact change is computed using the machine bank plus the coins inserted for the current transaction
- Methods in `VendingMachine` are `synchronized` so the current version remains thread-safe at object level
- The machine state is represented by an enum for simplicity

---

## High-level design used in code

The design is intentionally simple and clean:

- `VendingMachine` is the **main orchestrator**
- `model` package contains pure domain objects
- `service` package contains the change calculation logic
- `exception` package captures business failures explicitly

This keeps responsibilities separated:

- domain data stays in model classes
- business workflow stays in the machine class
- calculation logic stays behind an interface

---

## Package and class explanation

## 1. Orchestration layer

### `org.systemdesign.vendingmachine.VendingMachine`

This is the **core class** of the system. In an interview, I would describe it as the **aggregate root / coordinator**.

It owns:

- slot inventory: `Map<String, Slot>`
- machine coin bank: `Map<Coin, Integer>`
- coins inserted in the current transaction: `Map<Coin, Integer>`
- current machine state
- change calculation strategy

#### Key responsibilities

- manage inventory loading
- manage coin loading
- accept coins from user
- calculate current balance
- validate a purchase request
- compute change
- dispense product
- refund inserted coins on cancel
- expose stock and state for inspection

#### Key methods and what they do

- `loadSlot(String slotCode, Product product, int quantity)`
  - Creates a new slot or refills an existing one
  - Prevents mixing different products in the same slot

- `loadCoins(Map<Coin, Integer> coinsToAdd)`
  - Refills the machine coin bank

- `insertCoin(Coin coin)`
  - Adds one coin to the current transaction balance
  - Moves state to `ACCEPTING_MONEY`

- `currentBalance()`
  - Returns the total inserted amount in cents

- `purchase(String slotCode)`
  - Main business flow
  - Validates slot selection
  - Checks stock
  - Checks sufficient funds
  - Calculates change using `ChangeCalculator`
  - Dispenses product and updates inventory
  - Clears current transaction
  - Returns a `VendResult`

- `cancelAndRefund()`
  - Returns all inserted coins and resets the machine to `IDLE`

- `stock(String slotCode)`
  - Returns current quantity in a slot

- `state()`
  - Returns the current machine state

#### Why this class is important in interview terms

Interviewers usually want to see whether you can centralize business flow without making data classes too smart. `VendingMachine` is where the **use case orchestration** happens.

---

### `org.systemdesign.vendingmachine.VendingMachineState`

This enum models the machine lifecycle.

Values:

- `IDLE`
- `ACCEPTING_MONEY`
- `DISPENSING`
- `OUT_OF_SERVICE`

#### Why it exists

It makes the flow explicit and easy to reason about. In a more advanced design, this enum could be replaced by proper State Pattern classes, but for interview coding this is a good balance between clarity and speed.

---

### `org.systemdesign.vendingmachine.Main`

This is a small demo runner.

It shows:

- machine creation
- inventory loading
- coin loading
- coin insertion
- purchase execution
- output of dispensed product and returned change

This class is useful to quickly demonstrate the behavior after coding in an interview or practice session.

---

## 2. Model layer

The model layer contains domain objects that represent the data of the vending machine.

### `org.systemdesign.vendingmachine.model.Product`

A product is represented as a Java record:

- `id`
- `name`
- `priceInCents`

#### Responsibility

Represents an item that can be sold by the machine.

#### Important rule

The compact constructor validates that `priceInCents` is positive.

#### Interview note

Using cents instead of `double` is a very good practice because money calculations should avoid floating-point precision issues.

---

### `org.systemdesign.vendingmachine.model.Coin`

`Coin` is an enum with denomination values:

- `NICKEL(5)`
- `DIME(10)`
- `QUARTER(25)`

#### Responsibility

Represents accepted coin types and their monetary value.

#### Why enum is good here

- finite valid values
- type-safe input
- easy to extend with more denominations later

---

### `org.systemdesign.vendingmachine.model.Slot`

A `Slot` represents a machine compartment such as `A1`.

Fields:

- `code`
- `product`
- `quantity`

#### Responsibility

Represents stocked inventory for one product at one physical location.

#### Key behaviors

- `isOutOfStock()`
- `add(int amount)`
- `dispenseOne()`

#### Why this class matters

Instead of storing raw maps of slot-to-quantity and slot-to-product separately, wrapping them inside `Slot` makes the model cleaner and closer to the real-world concept.

---

### `org.systemdesign.vendingmachine.model.VendResult`

This record captures the result of a successful purchase.

Fields:

- `product`
- `paidAmount`
- `changeAmount`
- `changeCoins`

#### Responsibility

Provides a structured response to the caller after purchase, instead of returning only a boolean or printing directly to console.

#### Why it is a strong interview design choice

A result object is much better than primitive return values because it is extensible and self-descriptive.

---

## 3. Service layer

### `org.systemdesign.vendingmachine.service.ChangeCalculator`

This is an interface:

- `calculateChange(int amount, Map<Coin, Integer> availableCoins)`

#### Responsibility

Abstracts change-making logic away from `VendingMachine`.

#### Why this is good design

This is a classic interview point: if change calculation becomes complex, we should not hard-code it directly inside the machine class. By keeping it behind an interface, we can swap implementations easily.

Possible future implementations:

- greedy calculator
- DP/backtracking calculator
- region-specific denomination strategy

---

### `org.systemdesign.vendingmachine.service.GreedyChangeCalculator`

This is the current implementation of `ChangeCalculator`.

#### How it works

- sorts coins by descending value
- tries to use the largest denomination first
- respects machine coin availability
- throws `ChangeNotAvailableException` if exact change cannot be formed

#### Responsibility

Encapsulates the algorithm for returning change.

#### Interview trade-off discussion

Greedy works well for standard denominations used here. For arbitrary denominations, greedy may not always be optimal, so a different strategy may be required.

---

## 4. Exception layer

The exception package models business failures explicitly.

### `InvalidSlotSelectionException`
Thrown when the selected slot does not exist.

### `OutOfStockException`
Thrown when the selected slot exists but has zero quantity.

### `InsufficientFundsException`
Thrown when inserted money is less than product price.

### `ChangeNotAvailableException`
Thrown when the machine cannot return exact change.

#### Why custom exceptions are useful in interviews

They improve readability and make failure cases explicit. Instead of returning generic error strings everywhere, the system models domain failures cleanly.

---

## Purchase flow mapped to code

The main purchase flow in `VendingMachine.purchase(...)` is:

1. validate machine is operational
2. fetch slot by code
3. reject invalid slot
4. reject out-of-stock slot
5. compute current paid balance
6. compare balance with product price
7. compute change amount
8. merge machine coins and inserted coins for available change
9. calculate change through `ChangeCalculator`
10. change state to `DISPENSING`
11. dispense one product from slot
12. move inserted coins into machine bank
13. deduct returned change from machine bank
14. clear inserted transaction coins
15. move state back to `IDLE`
16. return `VendResult`

This is the exact kind of step-by-step explanation interviewers expect while you code.

---

## Why this solution is good for an LLD interview

It demonstrates:

- requirement clarification
- scope control
- domain-driven class identification
- separation of concerns
- extensibility through interfaces
- explicit failure handling
- simple but clean concurrency handling with `synchronized`
- readable, testable code

It is not over-engineered, which is important in interviews. The design is simple enough to finish in time, while still showing good engineering judgment.

---

## How to talk through the code in an interview

A simple structure is:

1. "I will first freeze scope."
2. "Then I will identify entities and actions."
3. "`VendingMachine` will orchestrate the use cases."
4. "`Product`, `Slot`, and `Coin` are my core model classes."
5. "Change logic is extracted behind `ChangeCalculator` for extensibility."
6. "I am using custom exceptions for clear business failures."
7. "I am keeping the first version in-memory and thread-safe using synchronized methods."
8. "If needed, I can extend this to support card payment, persistence, and State Pattern classes."

That answer sounds practical and senior during an interview.

---

## Run demo

```bash
mvn -pl vending-machine -am -DskipTests compile
java -cp vending-machine/target/classes org.systemdesign.vendingmachine.Main
```

## Run the custom test harness

```bash
mvn -pl vending-machine -am test-compile
java -cp vending-machine/target/classes:vending-machine/target/test-classes org.systemdesign.vendingmachine.VendingMachineTest
```
