// SPDX-License-Identifier: MIT
// Copyright 2023 Daniel Nilsson
package puzzle.solver;

import java.util.List;
import java.util.concurrent.ForkJoinPool;

/**
 * Main class for solving a puzzle challenge.
 */
public class MainSolveChallenge {

    public static void main(String[] args) {
        ForkJoinPool forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
        List<Piece> pieces = PieceFactory.getPiecesPruned();
        Challenge challenge = new Challenge(pieces, """

                  0  44
                  0  4444
                  00

                """);
        SolverByPiece solver = new SolverByPiece(challenge.getAvailablePieces(), challenge.getOccupied(), true,
                forkJoinPool);
        solver.solve();
    }

}
