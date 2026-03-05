# Facade Pattern

## What is it? (One-liner)
Facade provides a **simplified interface** to a complex subsystem. It doesn't add functionality — it simplifies access.

## When to Use (Interview Answer)
> "I'd use Facade when I want to provide a **simple entry point** to a complex subsystem. For example, placing an order involves inventory, payment, shipping, and notification services — the Facade gives the client one method: `placeOrder()`, hiding all the complexity behind it."

## How to Implement
```java
// Complex subsystems
public class InventoryService { boolean checkStock(String id) { ... } }
public class PaymentService  { String chargeCard(String card, double amt) { ... } }
public class ShippingService { String createShipment(String orderId) { ... } }

// Facade - one simple interface
public class OrderFacade {
    private final InventoryService inventory = new InventoryService();
    private final PaymentService payment = new PaymentService();
    private final ShippingService shipping = new ShippingService();

    public String placeOrder(String product, String card, double amount, String address) {
        inventory.checkStock(product);
        String txn = payment.chargeCard(card, amount);
        shipping.createShipment(txn);
        return txn;
    }
}

// Client: one call instead of many
orderFacade.placeOrder("PROD-001", "4111...", 59.99, "123 Main St");
```

## UML Structure
```
    Client
      │
      ▼
┌───────────────┐
│  OrderFacade  │  ◄── Simple interface
├───────────────┤
│ +placeOrder() │
└──────┬────────┘
       │ delegates to
  ┌────┼──────────┐
  ▼    ▼          ▼
Inventory Payment Shipping  ◄── Complex subsystems
```

## Real-World Examples
- `javax.faces.context.FacesContext` — simplifies JSF subsystem
- Spring's `JdbcTemplate` — simplifies JDBC operations
- SLF4J — facade over logging frameworks
- `java.net.URL.openStream()` — hides socket/connection complexity
- REST API controllers acting as facades

## Interview Deep-Dive Questions

**Q: Facade vs Adapter?**
| Facade | Adapter |
|--------|---------|
| Simplifies a complex interface | Converts one interface to another |
| Wraps multiple classes | Wraps one class |
| Defines a new, simpler interface | Makes existing interface compatible |

**Q: Does Facade violate SRP?**
> "No, its single responsibility IS to be the entry point. It delegates actual work to subsystems. But be careful not to let it become a 'God class' that orchestrates too much."

**Q: Does Facade restrict access to subsystems?**
> "No. Facade provides a convenient shortcut, but doesn't prevent direct access to subsystem classes."

## Key Points to Mention in Interview
1. Simplifies interaction with complex subsystem
2. Doesn't add new functionality — just a convenient layer
3. Subsystems are still accessible directly if needed
4. Reduces coupling between client and subsystem
5. Very common in enterprise apps (service layers, API gateways)
6. Mention JdbcTemplate or SLF4J as real examples
