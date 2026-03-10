# ❌⭕ Tic-Tac-Toe LLD — Complete Interview Guide

> ✅ **This is the CORRECT interview-ready design.** There is also a [tic-tac-toe2](../tic-tac-toe2/tic-tac-toe2-lld.md) module in this repo — that one is intentionally kept as a **BAD design / anti-pattern reference**. Do NOT use tic-tac-toe2 as a template. Study this module instead.

---

## 📁 Final Package Structure

```
org.tictactoe
├── Main.java                              ← Demo / entry point (simulates a full game)
├── model/
│   ├── enums/
│   │   ├── PieceType.java                 X, O
│   │   └── GameStatus.java                IN_PROGRESS, X_WON, O_WON, DRAW
│   ├── Player.java                        name + pieceType
│   ├── Board.java                         NxN grid, place piece, undo, display
│   ├── Move.java                          row, col, player (immutable record)
│   └── Cell.java                          row, col, current PieceType
├── strategy/
│   ├── WinningStrategy.java               interface — check if last move wins
│   ├── RowWinStrategy.java                O(1) per move via row counters
│   ├── ColumnWinStrategy.java             O(1) per move via column counters
│   ├── DiagonalWinStrategy.java           O(1) per move via diagonal counters
│   └── AntiDiagonalWinStrategy.java       O(1) per move via anti-diagonal counter
├── service/
│   └── GameService.java                   orchestrates game loop, validates moves, checks win/draw
├── factory/
│   └── GameFactory.java                   creates Board + Players + GameService with defaults
├── validator/
│   └── MoveValidator.java                 bounds check, cell-occupied check
└── exception/
    ├── InvalidMoveException.java
    └── GameOverException.java
```

---

## 🎤 Step 1 — Clarifying Questions (say these FIRST in the interview)

| # | Question to Ask | Why It Matters |
|---|-----------------|----------------|
| 1 | Is it always a 3×3 board, or should we support N×N? | Drives board size generalization |
| 2 | Always 2 players, or could there be more? | Affects turn rotation logic |
| 3 | Is it human vs human, or do we need an AI/Bot player? | May need a `BotPlayer` with move strategy |
| 4 | Can a player undo their last move? | Adds undo functionality → store move history |
| 5 | Do we need to persist game state (save/load)? | Adds serialization; skip for now |
| 6 | What's the win condition for N×N? N in a row, or always 3? | Affects winning strategy |
| 7 | Do we need a replay feature? | Move history enables replay |
| 8 | Is this a console game, or do we need a GUI/API? | Keep it console; UI is a separate concern |

> **Pro tip:** Asking these shows you think about scope before coding. For this solution, we'll assume: **N×N board, 2 players, human vs human, console-based, with undo support.**

---

## 🧠 Step 2 — How I Thought About This (mental model)

### Start with the nouns → those become classes

> "A tic-tac-toe game has a board. The board has cells. Players take turns placing pieces on cells. A move records which player placed which piece where. The game has a status: in progress, someone won, or draw."

- **Board** → `Board` (N×N grid of `Cell`s)
- **Cell** → `Cell` (row, col, current piece)
- **Player** → `Player` (name, piece type)
- **Move** → `Move` (row, col, player)
- **Game** → `GameService` (orchestrator)
- **Piece** → `PieceType` enum (X, O)
- **Status** → `GameStatus` enum

### Then find the verbs → those become methods/services

> "Make a move. Validate the move. Check if someone won. Check for draw. Undo a move. Display the board."

- Make move → `GameService.makeMove(row, col)`
- Validate → `MoveValidator.validate(board, row, col)`
- Check win → `WinningStrategy.checkWin(board, lastMove)`
- Check draw → `GameService` checks if `moveCount == N*N`
- Undo → `GameService.undoLastMove()`
- Display → `Board.display()`

### Why separate winning strategies?

The **naive approach** is to scan the entire board after every move → **O(N²) per move**.

The **optimized approach** is to maintain counters per row, column, and diagonal. After each move, increment the counter for that player. If any counter reaches N → that player wins. This is **O(1) per move**.

By making each check a separate `WinningStrategy`, we follow:
- **Single Responsibility**: each strategy checks one thing
- **Open/Closed**: add new win conditions (e.g., "four corners") without modifying existing code
- **Strategy Pattern**: swap or compose strategies

---

## 🔧 Step 3 — Design Patterns Used (and WHY)

### 1. Strategy — `WinningStrategy`

```java
public interface WinningStrategy {
    boolean checkWin(Board board, Move lastMove);
    void registerMove(Move move, int boardSize);
    void unregisterMove(Move move, int boardSize);
}
```

**Why:** There are 4 ways to win (row, column, diagonal, anti-diagonal). Each is an independent check. Tomorrow you can add "four corners" or "box" win for custom games — just add a new strategy, zero changes to `GameService`.

**O(1) Win Detection:** Each strategy maintains an `int[]` or `int` counter. On each move, increment the counter for the current player. If counter == N → win. No scanning needed.

```
RowWinStrategy:
  rowCounts[player][row]++
  if rowCounts[player][row] == N → player wins

ColumnWinStrategy:
  colCounts[player][col]++
  if colCounts[player][col] == N → player wins

DiagonalWinStrategy:
  if row == col → diagCount[player]++
  if diagCount[player] == N → player wins

AntiDiagonalWinStrategy:
  if row + col == N-1 → antiDiagCount[player]++
  if antiDiagCount[player] == N → player wins
```

### 2. Factory — `GameFactory`

```java
GameService game = GameFactory.createStandardGame("Alice", "Bob");
GameService bigGame = GameFactory.createCustomGame("Alice", "Bob", 5);
```

**Why:** Encapsulates the wiring of Board + Players + Strategies + Validator + GameService. The client doesn't need to know which strategies to compose.

### 3. Immutable Value Objects — `Move`

```java
public record Move(int row, int col, Player player) { }
```

**Why:** A move, once made, is a historical fact. Using a Java `record` (or immutable class) prevents accidental mutation and makes move history safe to iterate.

### 4. Separation of Concerns

| Concern | Class | Responsibility |
|---------|-------|---------------|
| State | `Board`, `Cell` | Hold grid data |
| Rules | `WinningStrategy`, `MoveValidator` | Enforce game rules |
| Flow | `GameService` | Orchestrate turns, enforce order |
| Creation | `GameFactory` | Build the game object graph |
| Errors | `InvalidMoveException`, `GameOverException` | Signal rule violations |

---

## ⚙️ Step 4 — Key Algorithms

### O(1) Win Detection

Instead of scanning the board after every move (O(N²)), we maintain counters:

```
On makeMove(row, col, playerIndex):
  rowCount[playerIndex][row]++     → if == N, player wins
  colCount[playerIndex][col]++     → if == N, player wins
  if (row == col)        diagCount[playerIndex]++   → if == N, player wins
  if (row + col == N-1)  antiDiagCount[playerIndex]++ → if == N, player wins
```

**Total: O(1) per move, O(N) space for counters.**

### Draw Detection

```
if (moveCount == N * N && no winner) → DRAW
```

No need to scan the board — just count moves.

### Undo Move

```
On undo(lastMove):
  board.clearCell(row, col)
  rowCount[playerIndex][row]--
  colCount[playerIndex][col]--
  if (row == col)        diagCount[playerIndex]--
  if (row + col == N-1)  antiDiagCount[playerIndex]--
  moveCount--
  switch back to previous player
```

**Why undo is possible:** Because we use additive counters (not a full scan), undo is just the reverse operation — decrement instead of increment.

---

## 🚨 Step 5 — Edge Cases (they WILL ask these)

| Edge Case | How It's Handled |
|-----------|-----------------|
| Move on occupied cell | `MoveValidator` checks `cell.isEmpty()` → throws `InvalidMoveException` |
| Move out of bounds | `MoveValidator` checks `0 <= row < N && 0 <= col < N` |
| Move after game is over | `GameService` checks `gameStatus != IN_PROGRESS` → throws `GameOverException` |
| Undo when no moves made | `GameService` checks `moveHistory.isEmpty()` → returns false |
| Board completely full, no winner | `moveCount == N*N` → status becomes `DRAW` |
| N=1 board | First move always wins (edge case handled naturally by counters) |
| Same player tries to play twice | `GameService` enforces turn order via `currentPlayerIndex` |
| Diagonal check on non-square cells | Diagonal strategy checks `row == col`; anti-diagonal checks `row + col == N-1` — only cells on diagonals are counted |

---

## 🗣️ Step 6 — How to Present This in an Interview

**Opening (1–2 min):**
> "Before I start designing, let me ask a few clarifying questions..."
> *(Ask the 8 questions above)*
> "OK, so I'll design for an N×N board, 2 human players, console-based, with undo support."

**High-level design (2 min):**
> "The core entities are Board, Cell, Player, Move, and GameService. I'll separate concerns into model, service, strategy, validator, and exception packages. The key insight is using counting-based O(1) win detection instead of scanning the board."

**Winning strategy (2 min):**
> "I'll use the Strategy pattern for win detection. Each strategy (row, column, diagonal, anti-diagonal) maintains counters. On each move, I increment the counter — if it reaches N, that player wins. This gives us O(1) win checks."

**Walk through a game (3–5 min):**
> Walk through `GameService.makeMove()`:
> 1. Validate move (bounds + occupied)
> 2. Place piece on board
> 3. Register move with all winning strategies
> 4. Check if any strategy reports a win
> 5. If no win and board full → draw
> 6. Switch to next player

**Undo support (1 min):**
> "Since winning strategies use additive counters, undo is just the reverse — decrement counters, clear the cell, pop from move history, switch player back."

**Edge cases (1–2 min):**
> Mention occupied cell, out of bounds, game already over, undo with no moves.

---

## 💡 Follow-up Questions They Might Ask

| Question | Your Answer |
|----------|------------|
| "How would you add a Bot/AI player?" | Create a `BotPlayer extends Player` with a `MoveStrategy` interface. Implement `RandomMoveStrategy` for easy, `MinimaxStrategy` for unbeatable. `GameService` calls `player.getMove(board)` instead of reading from console. |
| "How would you support more than 2 players on a larger board?" | `Player` list instead of 2 players. `currentPlayerIndex = (currentPlayerIndex + 1) % playerCount`. Winning strategies already use player-indexed counters — just extend the array size. |
| "How would you add a GUI?" | `Board` already has clean state accessors. Create a `GameRenderer` interface with `ConsoleRenderer` and `SwingRenderer` implementations. Observer pattern: board notifies renderer on state change. |
| "What if the board is huge (1000×1000)?" | The O(1) win detection already handles it. No scanning. Memory for counters is O(N × numPlayers) — totally fine for N=1000. |
| "How would you add persistence (save/load)?" | Serialize the move history (list of `Move` records). On load, replay moves onto a fresh board. This is the **Command pattern / Event Sourcing** approach. |
| "How would you make this thread-safe for online multiplayer?" | Add `synchronized` or `ReentrantLock` on `makeMove()`. Use websockets to broadcast moves. Each client maintains a local board copy and validates server-confirmed moves. |
| "Why not just check the entire board after each move?" | It works for 3×3, but doesn't scale. The counting approach is O(1) per move vs O(N²). In an interview, showing you think about scalability even for a "simple" problem is impressive. |

---

## 🎯 Step 7 — Complexity Analysis

| Operation | Time | Space |
|-----------|------|-------|
| Make a move | O(1) | — |
| Validate a move | O(1) | — |
| Check win (all strategies) | O(1) | — |
| Check draw | O(1) | — |
| Undo a move | O(1) | — |
| Display board | O(N²) | — |
| Total space for counters | — | O(N × players) |
| Move history space | — | O(N²) worst case |

---

## 🏗️ Step 8 — Class Diagram (Text)

```
┌─────────────────┐     creates      ┌──────────────────┐
│   GameFactory    │ ───────────────→ │   GameService    │
└─────────────────┘                   │                  │
                                      │  - board         │
                                      │  - players[]     │
                                      │  - strategies[]  │
                                      │  - validator     │
                                      │  - moveHistory   │
                                      │                  │
                                      │  + makeMove()    │
                                      │  + undoLastMove() │
                                      │  + getStatus()   │
                                      └────────┬─────────┘
                                               │ uses
                    ┌──────────────────────────┼──────────────────────┐
                    │                          │                      │
                    ▼                          ▼                      ▼
           ┌──────────────┐          ┌─────────────────┐    ┌──────────────────┐
           │    Board     │          │ WinningStrategy  │    │  MoveValidator   │
           │              │          │   «interface»    │    │                  │
           │  - cells[][] │          │                  │    │  + validate()    │
           │  - size      │          │  + checkWin()    │    └──────────────────┘
           │              │          │  + registerMove()│
           │  + placePiece│          │  + unregisterMove│
           │  + clearCell │          └────────┬─────────┘
           │  + display() │                   │
           └──────────────┘                   │ implemented by
                    │              ┌──────────┼──────────┬───────────────────┐
                    │              │          │          │                   │
                    ▼              ▼          ▼          ▼                   ▼
              ┌──────────┐  ┌──────────┐ ┌────────┐ ┌──────────┐  ┌───────────────┐
              │   Cell   │  │ RowWin   │ │ColWin  │ │DiagWin   │  │AntiDiagWin    │
              │          │  │ Strategy │ │Strategy│ │Strategy  │  │Strategy       │
              │ - row    │  └──────────┘ └────────┘ └──────────┘  └───────────────┘
              │ - col    │
              │ - piece  │        ┌──────────┐         ┌──────────┐
              └──────────┘        │  Player  │         │   Move   │
                                  │          │         │ «record» │
                                  │ - name   │         │          │
                                  │ - piece  │         │ - row    │
                                  └──────────┘         │ - col    │
                                                       │ - player │
                                                       └──────────┘
```

