package org.tictactoe2;

import org.tictactoe2.model.GameStatus;

public class PlayGame {

    public static void main(String[] args) {
        // This(tic-tac-toc2) is not a good example,
        // tic-tac-toc module is good approach, check that for better understanding of design and implementation of tic-tac-toc game.
        System.out.println("\n===>>> TicTacToe Game\n");
        TicTacToeGame game = new TicTacToeGame();
        game.initializeGame();
        GameStatus status = game.startGame();
        System.out.print("\n===>>> GAME OVER: ");
        switch (status) {
            case WIN:
                System.out.print(game.winner.name + " won the game");
                break;
            case DRAW:
                System.out.print(" Its a Draw!");
                break;
            default:
                System.out.print(" Game Ends");
                break;
        }

    }

}