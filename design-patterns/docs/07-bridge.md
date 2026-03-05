# Bridge Pattern

## What is it? (One-liner)
Bridge **decouples an abstraction from its implementation** so that the two can vary independently.

## When to Use (Interview Answer)
> "I'd use Bridge when I have two orthogonal dimensions of variation. For example, a Remote Control (abstraction) and Device (implementation) -- I want to add new remote types AND new device types independently, without a class explosion."

## The Problem It Solves (Class Explosion)
Without Bridge:
```
BasicRemote_TV, BasicRemote_Radio, BasicRemote_Speaker
AdvancedRemote_TV, AdvancedRemote_Radio, AdvancedRemote_Speaker
// 2 remotes x 3 devices = 6 classes. Adding 1 device = 2 more classes!
```

With Bridge:
```
2 Remote classes + 3 Device classes = 5 classes total
Adding 1 device = just 1 new class
```

## How to Implement
```java
// Implementation interface
public interface Device {
    void powerOn();
    void setVolume(int volume);
}

// Abstraction
public class RemoteControl {
    protected Device device;  // Bridge to implementation

    public RemoteControl(Device device) {
        this.device = device;
    }

    public void togglePower() { device.powerOn(); }
    public void volumeUp()    { device.setVolume(device.getVolume() + 10); }
}

// Refined Abstraction
public class AdvancedRemote extends RemoteControl {
    public void mute() { device.setVolume(0); }
}

// Usage: Any remote works with any device
RemoteControl tvRemote = new RemoteControl(new TV());
AdvancedRemote radioRemote = new AdvancedRemote(new Radio());
```

## UML Structure
```
    Abstraction                Implementation
┌─────────────────┐       ┌──────────────────┐
│  RemoteControl  │──────>│   <<interface>>   │
│  - device       │       │     Device        │
│  + togglePower()│       │  + powerOn()      │
└────────┬────────┘       │  + setVolume()    │
         │ extends        └────────┬──────────┘
┌────────┴────────┐       ┌───────┴───┬───────┐
│ AdvancedRemote  │       │           │       │
│ + mute()        │      TV        Radio   Speaker
└─────────────────┘
```

## Interview Deep-Dive Questions

**Q: Bridge vs Adapter?**
| Bridge | Adapter |
|--------|---------|
| Designed up-front | Applied after the fact |
| Both sides can vary independently | Adapts existing incompatible interface |
| Has-a relationship by design | Has-a as a workaround |

**Q: Bridge vs Strategy?**
> "Both use composition, but Bridge is structural (decouples abstraction from implementation across two hierarchies) while Strategy is behavioral (swaps algorithms at runtime within one class)."

## Real-World Examples
- JDBC API: `DriverManager` (abstraction) + DB drivers (implementation)
- Java logging: SLF4J (abstraction) + Logback/Log4j (implementation)
- GUI frameworks: Window (abstraction) + OS rendering (implementation)

## Key Points to Mention in Interview
1. Solves the class explosion problem with orthogonal dimensions
2. Both hierarchies can evolve independently
3. Uses composition over inheritance
4. "Designed up-front" vs Adapter which is "applied after the fact"
5. Mention JDBC or SLF4J as real Java examples
