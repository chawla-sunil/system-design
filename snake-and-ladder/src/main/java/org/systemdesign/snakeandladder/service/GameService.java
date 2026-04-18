package org.systemdesign.snakeandladder.service;

import org.systemdesign.snakeandladder.model.Board;
import org.systemdesign.snakeandladder.model.Dice;
import org.systemdesign.snakeandladder.model.Player;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * GameService orchestrates the Snake and Ladder game.
 *
 * Rules:
 * 1. Players take turns in order.
 * 2. Roll the dice, move forward by that many positions.
 * 3. If new position > board size, player stays (overshoot rule).
 * 4. If player lands on a snake head, slide down to tail.
 * 5. If player lands on a ladder bottom, climb up to top.
 * 6. If player rolls a 6, they get an extra turn.
 * 7. First player to reach exactly the last cell wins.
 */
public class GameService {
    private final Board board;
    private final Dice dice;
    private final Queue<Player> playerQueue;
    private Player winner;

    public GameService(Board board, Dice dice, List<Player> players) {
        this.board = board;
        this.dice = dice;
        this.playerQueue = new LinkedList<>(players);
        this.winner = null;
    }

    public void play() {
        System.out.println("=== Snake and Ladder Game Started! ===\n");

        while (winner == null) {
            Player currentPlayer = playerQueue.poll();
            playTurn(currentPlayer);

            if (winner == null) {
                playerQueue.add(currentPlayer);
            }
        }

        System.out.println("\n🏆 " + winner.getName() + " wins the game!");
    }

    private void playTurn(Player player) {
        boolean extraTurn;

        do {
            extraTurn = false;
            int diceValue = dice.roll();
            int oldPosition = player.getPosition();
            int newPosition = oldPosition + diceValue;

            System.out.println(player.getName() + " rolled a " + diceValue
                    + " | " + oldPosition + " → " + newPosition);

            if (!board.isValidPosition(newPosition)) {
                System.out.println("  ❌ Overshoot! Stays at " + oldPosition);
                continue;
            }

            // Apply snake or ladder
            newPosition = board.getFinalPosition(newPosition);
            player.setPosition(newPosition);

            System.out.println("  ✅ " + player.getName() + " is now at " + newPosition);

            if (board.isWinningPosition(newPosition)) {
                winner = player;
                return;
            }

            // Extra turn on rolling 6
            if (diceValue == 6) {
                System.out.println("  🎲 Rolled a 6! Extra turn.");
                extraTurn = true;
            }
        } while (extraTurn);
    }

    public Player getWinner() {
        return winner;
    }
}

