package org.systemdesign.snakeandladder;

import org.systemdesign.snakeandladder.model.*;
import org.systemdesign.snakeandladder.service.GameService;

import java.util.Arrays;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        // Define Snakes (head → tail, moves player DOWN)
        List<BoardEntity> entities = Arrays.asList(
                new BoardEntity(16, 6, BoardEntityType.SNAKE),
                new BoardEntity(47, 26, BoardEntityType.SNAKE),
                new BoardEntity(49, 11, BoardEntityType.SNAKE),
                new BoardEntity(56, 53, BoardEntityType.SNAKE),
                new BoardEntity(62, 19, BoardEntityType.SNAKE),
                new BoardEntity(64, 60, BoardEntityType.SNAKE),
                new BoardEntity(87, 24, BoardEntityType.SNAKE),
                new BoardEntity(93, 73, BoardEntityType.SNAKE),
                new BoardEntity(95, 75, BoardEntityType.SNAKE),
                new BoardEntity(98, 78, BoardEntityType.SNAKE),

                // Define Ladders (bottom → top, moves player UP)
                new BoardEntity(1, 38, BoardEntityType.LADDER),
                new BoardEntity(4, 14, BoardEntityType.LADDER),
                new BoardEntity(9, 31, BoardEntityType.LADDER),
                new BoardEntity(21, 42, BoardEntityType.LADDER),
                new BoardEntity(28, 84, BoardEntityType.LADDER),
                new BoardEntity(36, 44, BoardEntityType.LADDER),
                new BoardEntity(51, 67, BoardEntityType.LADDER),
                new BoardEntity(71, 91, BoardEntityType.LADDER),
                new BoardEntity(80, 100, BoardEntityType.LADDER)
        );

        Board board = new Board(100, entities);
        Dice dice = new Dice(1); // single die
        List<Player> players = Arrays.asList(new Player("Alice"), new Player("Bob"));

        GameService game = new GameService(board, dice, players);
        game.play();
    }
}