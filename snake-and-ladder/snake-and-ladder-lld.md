# Snake and Ladder — Low Level Design

## 1. Problem Statement
Design a Snake and Ladder game that supports multiple players on a configurable board.

## 2. Requirements Gathered (Interviewer Q&A)

| Question | Answer |
|----------|--------|
| Number of players? | 2+ (configurable) |
| Board size? | 100 (configurable) |
| Dice count? | 1 (configurable) |
| Overshoot rule? | Stay at current position |
| Extra turn on 6? | Yes |
| Can snake/ladder overlap? | No |
| Winning condition? | Reach exactly position 100 |

## 3. Class Diagram

```
┌─────────────┐       ┌─────────────┐
│    Dice      │       │   Player    │
│─────────────│       │─────────────│
│ numberOfDice │       │ name        │
│──────────────│       │ position    │
│ roll(): int  │       └─────────────┘
└──────────────┘
                        ┌─────────────────┐
┌──────────────┐       │   BoardEntity    │
│ BoardEntityType│      │─────────────────│
│──────────────│       │ start: int       │
│ SNAKE        │◄──────│ end: int         │
│ LADDER       │       │ type             │
└──────────────┘       └─────────────────┘
                              │
                              ▼
                       ┌──────────────┐
                       │    Board     │
                       │──────────────│
                       │ size: int    │
                       │ entityMap    │
                       │──────────────│
                       │ getFinalPosition(pos) │
                       │ isWinningPosition(pos)│
                       │ isValidPosition(pos)  │
                       └──────────────┘
                              │
                              ▼
                       ┌──────────────┐
                       │ GameService  │
                       │──────────────│
                       │ board        │
                       │ dice         │
                       │ playerQueue  │
                       │ winner       │
                       │──────────────│
                       │ play()       │
                       │ playTurn()   │
                       └──────────────┘
```

## 4. Design Decisions & Trade-offs

### Why `BoardEntity` instead of separate `Snake` and `Ladder` classes?
Both have the same structure (start → end). Using a single class with a `type` enum avoids duplication. If behavior diverges later (e.g., snakes with venom damage), we can refactor to an inheritance hierarchy.

### Why `Queue<Player>` for turn management?
A queue naturally models round-robin turn order. Poll the current player, play their turn, and add them back to the queue (unless they won).

### Why `Map<Integer, BoardEntity>` in Board?
O(1) lookup when a player lands on a position — much better than iterating a list every turn.

### Overshoot Rule
If `currentPosition + diceValue > boardSize`, the player stays. This is the standard rule.

### Extra Turn on 6
Rolling a 6 gives another turn. Implemented as a `do-while` loop inside `playTurn()`.

## 5. Extensibility Points

| Extension | How to add |
|-----------|-----------|
| Multiple dice | Change `Dice(numberOfDice)` constructor parameter |
| Crocodile/special entities | Add new `BoardEntityType` enum value |
| Configurable board size | Already supported via `Board(size, entities)` |
| Undo/replay | Add `Memento` pattern to save game state |
| Network multiplayer | Extract `GameService` behind an interface, add event-driven turns |

## 6. File Structure

```
snakeandladder/
├── Main.java                        # Entry point, wires the game
├── model/
│   ├── Board.java                   # Board with snake/ladder map
│   ├── BoardEntity.java             # A snake or ladder
│   ├── BoardEntityType.java         # SNAKE | LADDER enum
│   ├── Dice.java                    # Configurable dice
│   └── Player.java                  # Player with name and position
└── service/
    └── GameService.java             # Game orchestration logic
```

## 7. How I'd Walk Through This in an Interview

1. **Clarify requirements** — Ask 5-6 questions to narrow scope (2 mins)
2. **Identify entities** — Board, Player, Dice, Snake, Ladder, Game (3 mins)
3. **Draw class diagram** — Show relationships on whiteboard (5 mins)
4. **Discuss key algorithms** — Turn management, snake/ladder resolution (5 mins)
5. **Code the solution** — Start with models, then service, then main (30 mins)
6. **Discuss extensibility** — How to add features without breaking existing code (5 mins)
7. **Edge cases** — Overshoot, all players stuck in snake loop, board validation (5 mins)

