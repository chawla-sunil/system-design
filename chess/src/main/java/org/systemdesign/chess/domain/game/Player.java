package org.systemdesign.chess.domain.game;

import org.systemdesign.chess.domain.Color;
import org.systemdesign.chess.domain.piece.King;
import org.systemdesign.chess.domain.piece.Piece;

/**
 * Represents a player in a chess game.
 *
 * Responsibilities:
 * 1. Associate color with player
 * 2. Track player type (human, AI, etc.)
 * 3. Maintain player-specific state (time, rating, etc. for future expansion)
 */
public class Player {
    private final Color color;
    private final boolean isHuman;
    private String name;

    public Player(Color color, boolean isHuman, String name) {
        this.color = color;
        this.isHuman = isHuman;
        this.name = name != null ? name : color.toString();
    }

    public Color getColor() {
        return color;
    }

    public boolean isHuman() {
        return isHuman;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Finds and returns the king of this player's color on the given board.
     */
    public King getKing(Board board) {
        Piece king = board.getKing(color);
        if (king instanceof King) {
            return (King) king;
        }
        return null;
    }

    @Override
    public String toString() {
        return "Player{" +
                "color=" + color +
                ", isHuman=" + isHuman +
                ", name='" + name + '\'' +
                '}';
    }
}
