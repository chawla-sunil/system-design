package org.systemdesign.snakeandladder.model;

/**
 * Represents a board entity that moves a player from one position to another.
 * Used for both Snakes (move down) and Ladders (move up).
 */
public class BoardEntity {
    private final int start;
    private final int end;
    private final BoardEntityType type;

    public BoardEntity(int start, int end, BoardEntityType type) {
        this.start = start;
        this.end = end;
        this.type = type;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public BoardEntityType getType() {
        return type;
    }

    @Override
    public String toString() {
        return type + " [" + start + " → " + end + "]";
    }
}

