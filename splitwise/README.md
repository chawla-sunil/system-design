# Splitwise LLD (Java)

This module is written as a **complete low-level design interview answer** for the classic **Splitwise** problem. The goal is not only to show working code, but also to show **how a 6–7 year experienced engineer would structure the interview**, clarify scope, model the domain, explain trade-offs, and then code a clean in-memory solution.

---

## Interview mindset first: how I would approach this in a real LLD round

If an interviewer says **"Design Splitwise"**, I would not jump directly into classes.

I would structure the discussion like this:

1. **Clarify the scope**
2. **Freeze assumptions**
3. **Identify core entities and use cases**
4. **Design APIs / interactions**
5. **Discuss data model and business rules**
6. **Code the core workflow**
7. **Discuss extensibility, edge cases, and scaling**

That flow sounds senior and keeps the interview organized.

---

# 1-hour interview simulation

Below is how I would naturally drive a 1-hour interview.

## 0–5 min: Clarifying questions

I would begin with something like:

> Before I start coding, I want to clarify the expected scope so I can avoid over-engineering and focus on the right version.

### Questions to ask

- Are we designing a **single machine-round in-memory model**, or should I also discuss **database + APIs**?
- Do we support only **equal split**, or also **exact** and **percentage** split?
- Do we need both **group expenses** and **direct person-to-person expenses**?
- Should the system support **simplifying balances / settle up**?
- Do we need **expense history**?
- Do we need **partial settlement tracking** right now, or just compute balances?
- Do we care about **concurrency** in the coding round, or can I mention it as an extension?

## 5–10 min: Freeze the scope

For interview coding, I would explicitly freeze scope to this version:

### Functional scope in this implementation

- Add users
- Create groups
- Add users to groups
- Add expenses
- Support split types:
  - `EQUAL`
  - `EXACT`
  - `PERCENTAGE`
- Support both:
  - **group expenses**
  - **direct expenses**
- Show balances
- Show balances for a user
- Simplify balances into minimal settlement transactions
- Keep everything **in memory**

### Out of scope for first version

- persistent database
- authentication / authorization
- editing or deleting past expenses
- recurring expenses
- real payment integration
- notifications
- partial settlements with ledger mutation
- distributed locking

Saying this clearly is important. Interviewers like candidates who control scope.

---

## 10–15 min: Identify core entities

Now I would move from nouns to classes.

### Core entities

- `User`
- `Group`
- `Expense`
- `ExpenseShare`
- `BalanceEntry`
- `Settlement`
- `SplitInput`
- `SplitType`

### Why these entities exist

#### `User`
Represents a registered participant in the system.

#### `Group`
Represents a named collection of members like *Goa Trip*, *Flatmates*, or *Office Lunch*.

#### `Expense`
Represents one spending event:
- who paid
- how much was paid
- who participated
- how the amount was split
- whether the expense belongs to a group

#### `ExpenseShare`
Represents the final amount owed by a particular participant for one expense.

#### `SplitInput`
Represents caller input for split logic:
- for exact split: input value means exact amount
- for percentage split: input value means percentage

#### `BalanceEntry`
Represents a computed debt relation:
> debtor owes creditor X amount

#### `Settlement`
Represents a minimized settle-up instruction after debt simplification.

---

## 15–20 min: Key use cases / APIs

At this stage I would define the operations.

### Main APIs I want

- `addUser(...)`
- `createGroup(...)`
- `addMemberToGroup(...)`
- `addExpense(...)`
- `getAllBalances()`
- `getBalancesForGroup(...)`
- `getBalancesForUser(...)`
- `simplifyAllBalances()`
- `simplifyGroupBalances(...)`

In the implementation, these are exposed through the façade class:

- `org.systemdesign.splitwise.SplitwiseService`

This is intentional: in an interview, a façade/orchestrator makes the demo easy and keeps caller code clean.

---

## 20–30 min: Class design and responsibility split

A strong answer is not only about classes, but about **where responsibilities live**.

## High-level package design

```text
org.systemdesign.splitwise
├── Main.java
├── SplitwiseService.java         // façade / orchestrator
├── exception/
├── model/
├── repository/
├── service/
└── strategy/
```

## Responsibility split

### 1. Model layer
Pure domain objects.

### 2. Repository layer
In-memory storage abstraction for users, groups, and expenses.

### 3. Strategy layer
Encapsulates splitting algorithms so the system is open for extension.

### 4. Service layer
Contains business workflows like user creation, expense addition, balance calculation, and settlement simplification.

### 5. Facade layer
`SplitwiseService` wires everything together and gives a simple interview-friendly API.

---

# Design patterns used

## 1. Strategy Pattern
Used for split calculation.

### Why?
Because `EQUAL`, `EXACT`, and `PERCENTAGE` are different algorithms.

Classes:
- `SplitStrategy`
- `EqualSplitStrategy`
- `ExactSplitStrategy`
- `PercentageSplitStrategy`
- `SplitStrategyFactory`

### Interview explanation
> I extracted split calculation behind a strategy interface because the variation point in Splitwise is the split rule. This makes the solution extensible and aligns well with OCP.

---

## 2. Repository abstraction
Used for data access.

Classes:
- `UserRepository`
- `GroupRepository`
- `ExpenseRepository`
- corresponding in-memory implementations

### Why?
This avoids tightly coupling business logic to map/list storage and makes the design easier to evolve to DB-backed repositories later.

---

## 3. Facade / Orchestrator
Used via `SplitwiseService`.

### Why?
Interview demos become much simpler when the caller interacts with one clean entry point instead of constructing everything manually.

---

# Core workflows I would explain during the interview

## A. Add expense flow

When an expense is added, these steps happen:

1. validate payer exists
2. validate all participants exist
3. validate participants are unique
4. if group expense, validate payer and participants belong to the group
5. choose split strategy based on `SplitType`
6. compute participant shares
7. validate shares sum up to total amount
8. create and store the `Expense`

### Why this is the heart of the design
Because once expenses are modeled correctly, balances and settlement can be derived cleanly.

---

## B. Balance computation flow

Balances are computed from expenses.

For each expense:
- every participant owes their share to the payer
- if payer is also a participant, payer does not owe themself
- reverse debts are offset automatically

Example:
- Aman owes Bhavna ₹100
- Bhavna owes Aman ₹40

Final pairwise balance becomes:
- Aman owes Bhavna ₹60

This is implemented in `BalanceService`.

---

## C. Settle-up simplification flow

To simplify balances:

1. convert pairwise balances into **net balances per user**
2. positive means user should receive money
3. negative means user should pay money
4. use two priority queues:
   - max creditors
   - max debtors
5. greedily match biggest debtor with biggest creditor
6. emit minimized settlement instructions

This is implemented in `SettlementService`.

### Why this is a good interview point
It shows you can move from object design into algorithmic reasoning without overcomplicating the model.

---

# Important business rules in this implementation

- all money is stored as **integer cents/paise** (`long`) to avoid floating-point issues
- percentages are represented as whole numbers summing to **100**
- equal split distributes any rounding remainder to the earliest participants deterministically
- percentage split uses floor allocation first, then distributes leftover cents by fractional remainder order
- group expenses require all involved users to be members of that group
- duplicate participants are rejected
- invalid exact or percentage totals are rejected using `ValidationException`

---

# Detailed class-by-class explanation

## `SplitwiseService`
This is the main façade.

Responsibilities:
- add users
- create groups
- add expenses
- fetch balances
- simplify balances
- produce friendly text output for demo

### Why this class matters in interviews
It gives a neat top-level API and keeps the demo code focused on use cases instead of object wiring.

---

## `model.User`
Simple immutable record with:
- `id`
- `name`
- `email`

Validation ensures bad user objects are not created.

---

## `model.Group`
Represents a collection of member IDs.

Responsibilities:
- hold group metadata
- add members
- check membership

---

## `model.Expense`
Represents one expense event.

Fields:
- `id`
- `description`
- `paidByUserId`
- `totalAmountInCents`
- `shares`
- `splitType`
- `groupId`
- `createdAt`

### Why immutable expense objects are good
Past expenses are historical facts. Treating them as immutable simplifies reasoning.

---

## `model.ExpenseShare`
Represents a participant's contribution in the expense.

Example:
- `u1 -> 500`
- `u2 -> 300`
- `u3 -> 200`

---

## `service.ExpenseService`
This is the core workflow service.

Responsibilities:
- validate input
- validate group membership
- invoke split strategy
- generate expense IDs
- persist expense

This is the service I would likely code first during the interview.

---

## `service.BalanceService`
Responsibilities:
- derive pairwise balances from expenses
- offset reverse debts
- compute net balances

This service keeps all balance-related math away from orchestration code.

---

## `service.SettlementService`
Responsibilities:
- simplify debts using netting logic
- produce minimal payment instructions

---

## `strategy.EqualSplitStrategy`
If total amount is not divisible equally, leftover cents are distributed one by one to early participants.

Example:
- total = 1001
- participants = 3
- shares = 334, 334, 333

This is deterministic and interview-friendly.

---

## `strategy.ExactSplitStrategy`
Requires explicit amounts per participant.

Validation:
- every participant must have one input
- no duplicate inputs
- total of inputs must match the expense amount

---

## `strategy.PercentageSplitStrategy`
Requires explicit percentages.

Validation:
- each participant must have one percentage
- percentages must sum to 100
- leftover cents after flooring are distributed by fractional remainder order

This is a nice place to demonstrate careful handling of rounding.

---

# Sample interview narration while coding

If I were speaking live, I would say something like this:

> I’ll first create the immutable model objects so the domain is clear.

> Next I’ll create a split strategy interface because equal, exact, and percentage are different policies.

> Then I’ll build an expense service that validates payer, participants, and group membership, and stores a normalized expense object.

> After that, balance computation can be derived from stored expenses rather than maintained as mutable hidden state.

> Finally, I’ll add a settle-up service that converts pairwise balances into net balances and simplifies the required payments.

This sounds deliberate and senior.

---

# Edge cases interviewers often ask about

## 1. What if the payer is not part of the split?
Supported.
A payer can pay for others without being a participant.

## 2. What if percentages do not sum to 100?
Rejected with `ValidationException`.

## 3. What if exact shares do not add up to total?
Rejected.

## 4. What if the same participant is repeated twice?
Rejected.

## 5. What if a user in the expense is not part of the group?
Rejected for group expenses.

## 6. How do you avoid floating-point problems?
Use integer cents/paise.

## 7. How do you simplify circular debts?
Convert to net balances and settle greedily.

---

# Concurrency discussion for a senior interview

The current implementation is intentionally interview-friendly and in-memory.

### What is thread-safe here?
- in-memory repositories use synchronized methods for simple object-level safety

### What would I improve for production?
- use database transactions
- maintain versioned balance rows or append-only expense events
- use optimistic locking on balance updates
- expose APIs through controllers/application services
- use distributed locking only when truly necessary
- introduce idempotency keys for expense creation

### Interview answer I would give
> For the coding round I’m keeping it in-memory. For production, I’d likely store expenses as immutable events and derive/update balances transactionally with optimistic locking.

---

# Scaling discussion / production follow-up

If the interviewer pushes further, I would extend the answer like this:

## Persistence
- `users`
- `groups`
- `group_members`
- `expenses`
- `expense_shares`
- optional `settlements`

## APIs
- `POST /users`
- `POST /groups`
- `POST /groups/{id}/members`
- `POST /expenses`
- `GET /balances`
- `GET /groups/{id}/balances`
- `POST /settlements`

## Other product features
- expense comments
- receipt attachments
- recurring bills
- reminders
- audit log
- notification service
- activity feed

---

# Why this solution is interview-friendly

It demonstrates:

- requirement clarification
- clean scope control
- strong separation of concerns
- Strategy Pattern where it actually makes sense
- careful money handling
- direct and group expense support
- debt simplification algorithm
- readable and testable Java code

Most importantly, it is **not over-engineered**. That balance is exactly what good LLD interviews reward.

---

# How to run the demo

```bash
mvn -pl splitwise -am -DskipTests compile
java -cp splitwise/target/classes org.systemdesign.splitwise.Main
```

# How to run the smoke tests

```bash
mvn -pl splitwise -am test-compile
java -cp splitwise/target/classes:splitwise/target/test-classes org.systemdesign.splitwise.SplitwiseSmokeTest
```

---

# Final short interview answer you can actually speak

> I would start by clarifying whether we need equal-only or also exact and percentage splits, whether group expenses are needed, and whether settle-up is in scope. For the first version, I would keep it in-memory and support users, groups, expenses, balances, and debt simplification.
>
> Domain-wise, my key entities are User, Group, Expense, ExpenseShare, and BalanceEntry. The main variation point is split calculation, so I would use a Strategy Pattern for Equal, Exact, and Percentage splits.
>
> I would keep Expense immutable, because it is a historical fact. Balances can be derived from expenses rather than stored as fragile mutable state in the interview version. Then I’d add a SettlementService to simplify the resulting debts using net balance matching.
>
> This gives me a clean, extensible design that is easy to code within interview time and easy to extend later with persistence, APIs, and concurrency controls.

