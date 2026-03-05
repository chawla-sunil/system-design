# State Pattern

## What is it? (One-liner)
State allows an object to **alter its behavior when its internal state changes**. The object appears to change its class.

## When to Use (Interview Answer)
> "I'd use State when an object's behavior depends on its state, and it must change behavior at runtime. Instead of large if/else or switch blocks checking state, each state becomes its own class with its own behavior. Classic example: a vending machine or TCP connection."

## The Problem It Solves
```java
// WITHOUT State pattern (messy)
void insertMoney() {
    if (state == IDLE) { ... }
    else if (state == HAS_MONEY) { ... }
    else if (state == DISPENSING) { ... }  // Growing if/else!
}

// WITH State pattern (clean)
void insertMoney() {
    currentState.insertMoney(this);  // State handles its own behavior
}
```

## How to Implement
```java
public interface VendingMachineState {
    void insertMoney(VendingMachine machine, double amount);
    void selectProduct(VendingMachine machine, String product);
    void dispense(VendingMachine machine);
}

public class IdleState implements VendingMachineState {
    public void insertMoney(VendingMachine machine, double amount) {
        machine.addBalance(amount);
        machine.setState(new HasMoneyState());  // Transition!
    }
    public void selectProduct(VendingMachine machine, String product) {
        System.out.println("Insert money first!");  // Invalid in this state
    }
}

public class VendingMachine {
    private VendingMachineState state = new IdleState();

    public void insertMoney(double amount) {
        state.insertMoney(this, amount);  // Delegate to current state
    }
}
```

## State Transition Diagram
```
         insertMoney()
  [IDLE] ──────────────> [HAS_MONEY]
    ▲                      │ selectProduct()
    │                      ▼
    │      dispensed    [DISPENSING]
    └──────────────────────┘
```

## Real-World Examples
- **TCP Connection**: LISTEN, SYN_SENT, ESTABLISHED, CLOSE_WAIT, etc.
- **Order Status**: CREATED -> PAID -> SHIPPED -> DELIVERED
- **Thread States**: NEW, RUNNABLE, BLOCKED, WAITING, TERMINATED
- **Workflow engines**: Document approval states
- **Game characters**: Standing, Walking, Jumping, Attacking

## Interview Deep-Dive Questions

**Q: State vs Strategy?**
| State | Strategy |
|-------|----------|
| Object changes behavior by changing state | Client chooses the algorithm |
| States know about each other (transitions) | Strategies are independent |
| State transitions happen internally | Strategy set externally by client |
| Represents a state machine | Represents interchangeable algorithms |

**Q: Who controls state transitions?**
> "Two approaches: (1) States themselves decide the next state (decentralized — used in our example), or (2) The context controls transitions (centralized). Decentralized is more aligned with the pattern."

**Q: How to prevent invalid state transitions?**
> "Each state class only allows valid transitions by throwing exceptions or ignoring invalid operations. The state machine is self-documenting."

## Key Points to Mention in Interview
1. Eliminates large state-dependent conditionals (if/else, switch)
2. Each state encapsulates its own behavior
3. State transitions are explicit and visible
4. Follows Open/Closed Principle (new states without modifying existing)
5. Think of it as a **state machine** implemented with polymorphism
6. Thread lifecycle and TCP connection are great examples
