# ATM LLD Interview (1-hour simulation)

## 1) How to approach in interview (minute-by-minute)

### 0-5 mins: clarify scope
- Confirm ATM type: cash withdrawal + balance enquiry + cash deposit.
- Confirm out-of-scope: inter-bank settlement, card network protocol, UI rendering.
- Ask constraints:
  - PIN retry policy: 3 attempts then block card/account.
  - Supported notes: 100, 200, 500.
  - Per withdrawal limit: 10,000.
  - Need mini statement? yes (recent N transactions).

### 5-12 mins: define entities and relationships
- `Card` -> maps to exactly one `Account`.
- `Account` -> holds PIN, lock status, balance, transaction history.
- `AtmMachine` -> has cash inventory and session state.
- `BankService` -> account auth, debit/credit, statement.
- `AtmService` -> orchestration layer for ATM use cases.
- `CashDispenseStrategy` -> pluggable algorithm to choose notes.

### 12-18 mins: define states and use-case flow
- Session state:
  - `IDLE` -> `CARD_INSERTED` -> `AUTHENTICATED` -> `IDLE`
- Operations by state:
  - `insertCard` only in `IDLE`
  - `enterPin` only in `CARD_INSERTED`
  - `withdraw/deposit/checkBalance/miniStatement` only in `AUTHENTICATED`
  - `ejectCard` allowed from any state, always moves to `IDLE`

### 18-30 mins: discuss class design and interfaces
- Interview-friendly interfaces:
  - `BankService` abstraction so real CBS integration can replace in-memory impl.
  - `CashDispenseStrategy` abstraction for different algorithms.
- Core classes:
  - `Account`, `Card`, `TransactionRecord`, `CashInventory`.
  - `AtmMachine` keeps machine-level state.
  - `AtmService` enforces workflow + business rules.

### 30-45 mins: code critical flows
- Authentication with retry/lock.
- Withdrawal flow with rollback if ATM cannot dispense exact notes.
- Deposit flow with denomination validation.
- Statement retrieval.

### 45-55 mins: edge cases + error handling
- Wrong PIN until lock.
- Withdraw > per transaction limit.
- Withdraw amount not compatible with notes.
- ATM low on specific denominations.
- Unsupported note on deposit.

### 55-60 mins: test strategy + extensions
- Unit tests for happy path and failure path.
- Mention future improvements:
  - Separate state pattern classes.
  - Database-backed bank service.
  - Double-entry ledger and idempotency keys.
  - Concurrency and distributed locks in real deployment.

---

## 2) Requirements and assumptions

### Functional
1. Insert card and authenticate with PIN.
2. Balance enquiry.
3. Withdrawal.
4. Cash deposit.
5. Mini statement.
6. Eject card.

### Non-functional (interview level)
1. Correctness of money operations.
2. Extensibility via interfaces and strategy.
3. Clear state transition control.
4. Testable code with small focused units.

### Assumptions
- Single ATM session at a time per machine instance.
- In-memory bank implementation for interview/demo.
- Amounts are integer-valued currency units (no fractional cents in note logic).

---

## 3) Design choices explained

1. **Service orchestration (`AtmService`)**
   - Keeps use-case logic in one place and avoids fat entities.
2. **`BankService` interface**
   - Makes ATM independent from concrete banking backend.
3. **`CashDispenseStrategy` interface**
   - Allows replacing greedy algorithm with optimal/backtracking strategy later.
4. **Rollback in withdrawal**
   - Debit account first, but if note composition fails, credit back immediately.
5. **Transaction recording**
   - Success/failure entries provide auditability and statement support.

---

## 4) Implemented class map

- `enums`
  - `SessionState`
  - `TransactionType`
  - `TransactionStatus`
- `exception`
  - `AtmException`
  - `InvalidAtmOperationException`
  - `AuthenticationException`
  - `InsufficientFundsException`
  - `CashUnavailableException`
- `model`
  - `Card`
  - `Account`
  - `TransactionRecord`
  - `CashInventory`
- `strategy`
  - `CashDispenseStrategy`
  - `GreedyCashDispenseStrategy`
- `service`
  - `BankService`
  - `InMemoryBankService`
  - `AtmMachine`
  - `AtmService`
- `Main`
  - Demo run with seeded account/card/cash.

---

## 5) Complexity notes (for interview discussion)

- Withdrawal note planning (greedy): `O(d)` where `d` = number of denominations.
- Balance/deposit/authentication: `O(1)` average with hash map lookup.
- Statement fetch: `O(k)` where `k` = requested recent transactions.

---

## 6) How to run

From project root:

```bash
mvn -pl atm test
mvn -pl atm exec:java -Dexec.mainClass=org.systemdesign.atm.Main
```

If `exec-maven-plugin` is not configured globally, run from IDE using `Main`.

