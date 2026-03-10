# ❌⭕ Tic-Tac-Toe 2 — LLD Anti-Pattern Guide (What NOT to Do)

> ⚠️ **WARNING:** This module is an example of a **BAD** LLD design. It is kept here intentionally as a "what NOT to do" reference. For the correct, interview-ready approach, see [**tic-tac-toe**](../tic-tac-toe/tic-tac-toe-lld.md).

---

## 🚫 Why This Design Would FAIL an Interview

This is the kind of code you'd write in a 10-minute YouTube tutorial — it works, but an interviewer will tear it apart. Here's every problem, why it matters, and how the [tic-tac-toe](../tic-tac-toe/) module fixes it.

---

## 📁 Package Structure

```
org.tictactoe2
├── PlayGame.java                  ← Entry point
├── TicTacToeGame.java             ← GOD CLASS — game logic, win check, I/O all in one
└── model/
    ├── PieceType.java             ← enum: X, O
    ├── PlayingPiece.java          ← base class with public field
    ├── PlayingPieceX.java         ← extends PlayingPiece (unnecessary subclass)
    ├── PlayingPieceO.java         ← extends PlayingPiece (unnecessary subclass)
    ├── Player.java                ← public fields + getters/setters
    ├── Board.java                 ← public fields, O(N²) free cell scan
    └── GameStatus.java            ← enum: DRAW, WIN (missing IN_PROGRESS)
```

---

## 🔴 Problem-by-Problem Breakdown

### 1. God Class — `TicTacToeGame` does EVERYTHING

```java
public class TicTacToeGame {
    // Game state management ← should be in GameService
    // Win detection logic   ← should be in WinningStrategy
    // User I/O (Scanner)    ← should be in a Controller/UI layer
    // Board manipulation    ← should be in Board
}
```

**Why it's bad:**
- Violates **Single Responsibility Principle** — 4 reasons to change in one class
- Can't test win detection without running the full game loop
- Can't swap console I/O for a GUI or API without rewriting the entire class

**How tic-tac-toe fixes it:**
| Concern | tic-tac-toe2 (bad) | tic-tac-toe (good) |
|---------|-------------------|-------------------|
| Game flow | `TicTacToeGame` | `GameService` |
| Win detection | `TicTacToeGame.checkForWinner()` | `WinningStrategy` interface (4 implementations) |
| Move validation | inline `if` in `startGame()` | `MoveValidator` class |
| Object creation | inline `new` calls | `GameFactory` |

---

### 2. O(N) Win Detection Per Check — `checkForWinner()` Scans Entire Rows/Cols

```java
public boolean checkForWinner(int row, int column, PieceType pieceType) {
    // Scans ENTIRE row → O(N)
    for (int i = 0; i < gameBoard.size; i++) {
        if (gameBoard.board[row][i] == null || gameBoard.board[row][i].pieceType != pieceType) {
            rowMatch = false;
            break;
        }
    }
    // Same for column → O(N)
    // Same for diagonal → O(N)
    // Same for anti-diagonal → O(N)
    // Total: O(4N) per move = O(N)
}
```

**Why it's bad:**
- **O(N) per move** — scans up to 4×N cells after every single move
- For a 1000×1000 board, that's 4000 checks per move
- The interviewer WILL ask "can you do better?"

**How tic-tac-toe fixes it — O(1) per move:**
```java
// Maintain counters. On each move, increment. If counter == N → win.
rowCount[playerIndex][row]++     → if == N, player wins    // O(1)
colCount[playerIndex][col]++     → if == N, player wins    // O(1)
diagCount[playerIndex]++         → if == N, player wins    // O(1)
antiDiagCount[playerIndex]++     → if == N, player wins    // O(1)
// Total: O(1) per move
```

---

### 3. Public Fields Everywhere — No Encapsulation

```java
public class Player {
    public String name;              // ← public field!
    public PlayingPiece playingPiece; // ← public field!
}

public class Board {
    public int size;                 // ← public field!
    public PlayingPiece[][] board;   // ← public field, raw array exposed!
}

public class PlayingPiece {
    public PieceType pieceType;      // ← public field!
}
```

**Why it's bad:**
- Anyone can do `player.name = null` or `board.board[0][0] = anything` — no validation
- Can't add validation logic later without breaking all callers
- Exposes internal representation — violates **Information Hiding**
- An interviewer sees `public` fields and immediately flags it

**How tic-tac-toe fixes it:**
```java
// Private fields + controlled access
public class Player {
    private final String name;        // ← final + private
    private final PieceType pieceType;
    // Only getters, no setters — immutable
}
public record Move(int row, int col, Player player) { }  // ← Java record = immutable
```

---

### 4. Unnecessary Inheritance — `PlayingPieceX` / `PlayingPieceO`

```java
public class PlayingPieceX extends PlayingPiece {
    public PlayingPieceX() { super(PieceType.X); }
}
public class PlayingPieceO extends PlayingPiece {
    public PlayingPieceO() { super(PieceType.O); }
}
```

**Why it's bad:**
- These subclasses add **ZERO behavior**. They just call `super()` with a different enum.
- Two extra files that do nothing. Inheritance for the sake of inheritance.
- This is over-engineering and the interviewer will ask "why not just use the enum directly?"

**How tic-tac-toe fixes it:**
```java
// No PlayingPiece class at all. Just use PieceType enum directly.
public enum PieceType { X, O }
board.placePiece(row, col, PieceType.X);  // Simple and direct
```

---

### 5. No Undo Support — Can't Reverse a Move

```java
// tic-tac-toe2: No move history, no undo.
// Once placed, a piece is permanent.
// If the interviewer asks "can you add undo?" → full rewrite.
```

**How tic-tac-toe fixes it:**
```java
// Move history + reversible counters
List<Move> moveHistory = new ArrayList<>();
// Undo = pop last move, decrement counters, clear cell. O(1).
public boolean undoLastMove() {
    Move last = moveHistory.remove(moveHistory.size() - 1);
    board.clearCell(last.row(), last.col());
    strategies.forEach(s -> s.unregisterMove(last, board.getSize()));
    return true;
}
```

---

### 6. No Design Patterns — Zero Extensibility

| Pattern | tic-tac-toe2 | tic-tac-toe |
|---------|-------------|-------------|
| **Strategy** | ❌ Win check is a hard-coded method | ✅ 4 `WinningStrategy` implementations, pluggable |
| **Factory** | ❌ Objects created inline with `new` | ✅ `GameFactory` encapsulates wiring |
| **Immutable Records** | ❌ Mutable `Player` with public fields | ✅ `Move` is a Java `record`, `Player` is effectively immutable |
| **Validator** | ❌ Validation inline in game loop | ✅ `MoveValidator` as a separate class |

**An interviewer expects patterns.** If you write tic-tac-toe2 style code, you'll hear:
- "How would you add a new win condition?" → ❌ Modify `checkForWinner()` (violates Open/Closed)
- "How would you test win detection alone?" → ❌ Can't, it's buried in `TicTacToeGame`
- "How would you add a bot player?" → ❌ Full rewrite of the game loop

---

### 7. Missing `IN_PROGRESS` Status

```java
public enum GameStatus {
    DRAW,
    WIN
    // ❌ No IN_PROGRESS — how do you know the game is still running?
}
```

**How tic-tac-toe fixes it:**
```java
public enum GameStatus {
    IN_PROGRESS,
    X_WON,     // ← tells you WHO won
    O_WON,
    DRAW;
    public boolean isOver() { return this != IN_PROGRESS; }
}
```

---

### 8. Scanner (I/O) Inside Game Logic

```java
public GameStatus startGame() {
    Scanner inputScanner = new Scanner(System.in);  // ← I/O mixed with logic
    String s = inputScanner.nextLine();
    // ...
}
```

**Why it's bad:**
- Can't test the game without a real console
- Can't run in a web server, GUI, or bot mode
- I/O and business logic are coupled

**How tic-tac-toe fixes it:**
```java
// GameService.makeMove(row, col) — pure logic, no I/O
// Main.java reads input and calls gameService.makeMove()
// Tomorrow: swap console for REST API or bot — zero GameService changes
```

---

## 📊 Side-by-Side Comparison

| Aspect | tic-tac-toe2 ❌ | tic-tac-toe ✅ |
|--------|----------------|---------------|
| **Win detection** | O(N) scan per move | O(1) counters per move |
| **Extensibility** | Modify existing code to add features | Add new Strategy class, zero existing changes |
| **Testability** | Need console input to test anything | Call `gameService.makeMove()` in JUnit |
| **Encapsulation** | Public fields everywhere | Private final + getters, records |
| **Undo support** | Not possible without rewrite | Built-in via reversible counters |
| **Separation of concerns** | God class does everything | 6 focused classes |
| **Design patterns** | None | Strategy, Factory, Immutable Records |
| **Interview impression** | "Beginner — works but not production quality" | "Senior — clean, extensible, testable" |
| **Files** | 9 files (2 are useless subclasses) | 14 files (each earns its existence) |

---

## 🎓 What to Learn From This

This module exists to teach you **what interviewers DON'T want to see**:

1. ❌ **God classes** — everything in one place
2. ❌ **O(N) when O(1) is possible** — always ask "can I do better?"
3. ❌ **Public fields** — encapsulation is not optional
4. ❌ **Inheritance for no reason** — prefer composition, use enums for variant types
5. ❌ **I/O mixed with logic** — separate concerns
6. ❌ **No patterns** — you're expected to use Strategy, Factory, Observer where appropriate
7. ❌ **No extensibility story** — "how would you add X?" should never require a rewrite

> **Bottom line:** tic-tac-toe2 would get a "not hire" in an LLD interview. [tic-tac-toe](../tic-tac-toe/tic-tac-toe-lld.md) would get a "strong hire."

---

## ➡️ What to Study Instead

Go to [**tic-tac-toe/tic-tac-toe-lld.md**](../tic-tac-toe/tic-tac-toe-lld.md) for:
- ✅ Proper clarifying questions
- ✅ O(1) win detection algorithm
- ✅ Strategy, Factory, Immutable Records patterns
- ✅ Undo support
- ✅ Edge case handling
- ✅ Interview presentation script
- ✅ Follow-up Q&A

