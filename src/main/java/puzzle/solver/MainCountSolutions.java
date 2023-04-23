// SPDX-License-Identifier: MIT
// Copyright 2023 Daniel Nilsson
package puzzle.solver;

import java.util.List;
import java.util.concurrent.ForkJoinPool;

/**
 * Main class for counting total number of solutions of an empty board.
 */
public class MainCountSolutions {

    private static final boolean USE_ALTERNATE_SOLVER = false;
    private static final boolean USE_WARMUP = true;

    public static void main(String[] args) {
        ForkJoinPool forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
        List<Piece> pieces = PieceFactory.getPiecesPruned();
        // Both solvers can be used here, SolverByPosition is a bit faster
        Solver solver;
        if (USE_ALTERNATE_SOLVER) {
            solver = new SolverByPiece(pieces, 0L, false, forkJoinPool);
        } else {
            solver = new SolverByPosition(pieces, forkJoinPool);
        }
        if (USE_WARMUP) {
            // Warmup run to get all code JIT compiled
            solver.solve();
        }
        solver.solve();
    }

}
