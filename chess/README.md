# Chess Game — LLD Interview Guide

> Java · Senior level (6-7 yrs) · 60-min interview

---

## 1. Requirements Clarification (ask first)

- 2-player local game (human vs human)? → Yes (MVP)
- Undo/redo? → Out of scope
- AI opponent? → Out of scope
- Persist games? → No, in-memory only
- Time controls? → No

**Nouns → classes | Verbs → methods**

| Noun | Class | Key Methods |
|------|-------|-------------|
| Board | `Board` | placePiece, removePiece, getPieceAt |
| Square / Cell | `Position` | getRow, getCol, toAlgebraic |
| Piece | `Piece` (abstract) | getValidMoves, canCaptureAt |
| Player | `Player` | getColor, getName |
| Move | `Move` | getFrom, getTo, getMoveType |
| Game | `Game` | makeMove, getValidMoves, resign |
| Rules | `GameRules` | isInCheck, isMoveLegal, isCheckmate, isStalemate |

---

## 2. Chess Rules (relevant to design)

| Rule | Implementation note |
|------|---------------------|
| White moves first | `currentPlayer` initialized to WHITE |
| Cannot land on own color | validated inside `getValidMoves` |
| King cannot move into check | `isMoveLegal` simulates move then calls `isInCheck` |
| Castling | King + Rook unmoved, path clear, king not passing through check |
| En passant | Last pawn double-push tracked on `Board` |
| Pawn promotion | Pawn replaced on reaching rank 1 / rank 8 |
| Fifty-move rule | Counter on `Board`; reset on capture or pawn move |
| Checkmate | In check AND no legal move exists |
| Stalemate | NOT in check AND no legal move exists |

---

## 3. Package Structure

```
org.systemdesign.chess/
├── Main.java
├── domain/
│   ├── Color.java              # enum WHITE, BLACK
│   ├── ChessPieceType.java     # enum PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING
│   ├── Position.java           # immutable value object (row, col 0-7)
│   ├── game/
│   │   ├── Board.java          # Map<Position,Piece> — mutable state
│   │   ├── Player.java         # color + name
│   │   ├── Move.java           # immutable record (from, to, piece, type)
│   │   ├── MoveType.java       # enum NORMAL, CASTLING, EN_PASSANT, PROMOTION
│   │   └── GameStatus.java     # enum ACTIVE, CHECKMATE, STALEMATE, RESIGNED
│   └── piece/
│       ├── Piece.java          # abstract base
│       ├── Pawn.java
│       ├── Knight.java
│       ├── Bishop.java
│       ├── Rook.java
│       ├── Queen.java
│       └── King.java
├── service/
│   ├── Game.java               # facade + orchestrator
│   └── GameRules.java          # rule engine
└── factory/
    └── PieceFactory.java       # piece creation + board initialization
```

---

## 4. Class Design

### `Position` — Immutable Value Object
```java
public final class Position {
    private final int row, col;          // 0-7
    public String toAlgebraic() { ... }  // "e4"
    // equals + hashCode — required for HashMap key
}
```
Immutable so it can be a reliable `HashMap` key; prevents accidental mutation; thread-safe.

---

### `Piece` — Abstract Strategy
```java
public abstract class Piece {
    protected Color color;
    protected ChessPieceType type;
    protected Position position;
    protected boolean hasMoved;

    public abstract Set<Position> getValidMoves(Board board);
    public abstract boolean canCaptureAt(Position target, Board board);
}
```

| Piece | Movement | Special rules |
|-------|----------|---------------|
| Pawn | Forward 1 (2 on first move), diagonal capture | Promotion, en passant |
| Knight | 8 L-shapes | Jumps over pieces |
| Bishop | 4 diagonals, any distance | Blocked by pieces |
| Rook | 4 straights, any distance | Blocked; participates in castling |
| Queen | 8 directions, any distance | Rook + Bishop combined |
| King | 8 adjacent squares | Castling; cannot move into check |

---

### `Board` — Mutable State Container
```java
Map<Position, Piece> squares;   // O(1) lookup
```
Key methods: `getPieceAt`, `placePiece`, `removePiece`, `copy()` (for move simulation), `getKingPosition(Color)`.

---

### `Move` — Immutable Record (Builder Pattern)
```java
Move move = new Move.Builder(from, to, piece)
    .capturedPiece(opponent)
    .moveType(MoveType.PROMOTION)
    .promotionPiece(ChessPieceType.QUEEN)
    .build();
```

---

### `GameRules` — Rule Engine (Single Responsibility)
```java
boolean isInCheck(Color color, Board board);
boolean isMoveLegal(Move move, Board board);
boolean isCheckmate(Color color, Board board);
boolean isStalemate(Color color, Board board);
boolean canCastle(Color color, boolean kingside, Board board);
```

---

### `Game` — Facade & Orchestrator
```java
public boolean makeMove(Position from, Position to);
public Set<Position> getValidMoves(Position from);
public void resign();
public GameStatus getGameStatus();
```
Single entry point hiding `Board`, `GameRules`, `Player`, and move history.

---

## 5. Key Algorithms

### Check Detection — O(n), n ≤ 16 opponent pieces
```
for each opponent piece:
    if piece.canCaptureAt(kingPosition, board):
        return true
return false
```

### Move Legality — O(n)
```
boardCopy = board.copy()
simulate move on boardCopy
return !isInCheck(currentColor, boardCopy)
```
Pinned pieces are handled for free — any move that exposes the king is rejected.

### Checkmate / Stalemate — O(p × m × n)
```
inCheck = isInCheck(color, board)
hasLegal = any piece has at least one legal move

checkmate = inCheck  && !hasLegal
stalemate = !inCheck && !hasLegal
```

### `makeMove` Flow
```
1.  getPieceAt(from)           — must belong to currentPlayer
2.  piece.getValidMoves()      — candidate squares
3.  to in validMoves?
4.  isMoveLegal(move)          — simulate + check detection
5.  board.execute(move)        — place / remove pieces
6.  update castling rights, 50-move counter, hasMoved flags
7.  history.push(move)
8.  swap currentPlayer
9.  isCheckmate / isStalemate check
10. return true
```

---

## 6. Design Patterns

| Pattern | Where | Why |
|---------|-------|-----|
| **Strategy** | `Piece` subclasses | Each piece owns its movement logic |
| **Factory** | `PieceFactory` | Centralized, consistent creation |
| **Builder** | `Move.Builder` | Optional fields (capture, promotion) |
| **Immutable** | `Position`, `Move` | Safe as Map keys; reliable history |
| **Facade** | `Game` | Hides subsystem complexity |
| **State** | `GameStatus` enum | Controls allowed transitions |

---

## 7. Complexity Summary

| Operation | Time | Space |
|-----------|------|-------|
| `getPieceAt` | O(1) | — |
| `getValidMoves` (per piece) | O(1)–O(28) | — |
| `isInCheck` | O(n), n ≤ 16 | — |
| `isMoveLegal` | O(n) | O(n) board copy |
| `isCheckmate` / `isStalemate` | O(p·m·n) | O(n) |
| `makeMove` | O(p·m·n) | O(n) |

p ≤ 16 pieces, m ≤ 27 moves, n ≤ 50 check-scan ops → always fast.

---

## 8. Common Interview Questions

**Q: How do you prevent moving into check?**
`isMoveLegal` simulates the move on a board copy and calls `isInCheck`. If the king is still attacked, the move is rejected. Pinned pieces are handled automatically.

**Q: Why is `Position` immutable?**
Immutable objects are safe `HashMap` keys (stable `hashCode`). They also prevent accidental board-state corruption and are inherently thread-safe.

**Q: How is castling validated?**
`GameRules.canCastle` verifies: king and rook have not moved, squares between are empty, king is not currently in check, and king does not pass through an attacked square.

**Q: What is the complexity of checkmate detection?**
O(p × m × n) — p pieces × m candidate moves × O(n) legality check per move. With p ≤ 16, m ≤ 27, n ≤ 50, this is always fast in practice.

**Q: How would you implement undo?**
Keep `Board` snapshots alongside the immutable `Move` stack, or replay from the start excluding the last move. Immutable `Move` objects make the history trustworthy.

**Q: How would you scale to online multiplayer?**
Synchronize `Board` writes (lock or CAS), transport moves over WebSocket / message queue, keep game logic stateless w.r.t. I/O, handle concurrent-move conflicts with optimistic locking.

**Q: How would you add an AI player?**
Make `Player` abstract. `HumanPlayer` reads input; `AIPlayer` implements Minimax + alpha-beta pruning using `getValidMoves`. `Game` and `Board` need no changes.

---

## 9. Quick Run

```bash
cd chess
mvn clean compile
mvn exec:java -Dexec.mainClass="org.systemdesign.chess.Main"
```
