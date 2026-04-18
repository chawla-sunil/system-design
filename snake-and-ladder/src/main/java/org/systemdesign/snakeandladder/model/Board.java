package org.systemdesign.snakeandladder.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Board holds the snakes and ladders mapped by their start position.
 * When a player lands on a start position, they are moved to the end position.
 */
public class Board {
    private final int size;
    private final Map<Integer, BoardEntity> entityMap; // start position → entity

    public Board(int size, List<BoardEntity> entities) {
        this.size = size;
        this.entityMap = new HashMap<>();
        for (BoardEntity entity : entities) {
            if (entityMap.containsKey(entity.getStart())) {
                throw new IllegalArgumentException("Duplicate entity at position: " + entity.getStart());
            }
            entityMap.put(entity.getStart(), entity);
        }
    }

    public int getSize() {
        return size;
    }

    /**
     * Returns the final position after applying any snake/ladder at the given position.
     */
    public int getFinalPosition(int position) {
        if (entityMap.containsKey(position)) {
            BoardEntity entity = entityMap.get(position);
            System.out.println("  ⚡ Hit " + entity);
            return entity.getEnd();
        }
        return position;
    }

    public boolean isWinningPosition(int position) {
        return position == size;
    }

    public boolean isValidPosition(int position) {
        return position <= size;
    }
}

