// SPDX-License-Identifier: MIT
// Copyright 2023 Daniel Nilsson
package puzzle.solver;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.LongAdder;

/**
 * Solve puzzle by trying to fit one piece at a time, trying every variant of
 * each piece.
 * This solver can solve a challenge or count total number of solutions.
 */
public class SolverByPiece implements Solver {

    private final List<Piece> pieces;
    private final long initialOccupied;
    private final boolean printSolution;
    private final ForkJoinPool forkJoinPool;

    private final LongAdder solutions = new LongAdder();
    private final LongAdder tries = new LongAdder();

    private Instant start;

    /**
     * Create a new solver.
     *
     * @param pieces the available pieces.
     * @param initialOccupied the bit board of initially occupied positions.
     * @param printSolution true to print every solution that is found to
     * {@link System#out}, false to only print final statistics (faster).
     * @param forkJoinPool thread pool to use for the search.
     */
    public SolverByPiece(List<Piece> pieces, long initialOccupied, boolean printSolution, ForkJoinPool forkJoinPool) {
        this.pieces = new ArrayList<>(pieces);
        // It is most effective to try most constrained pieces first, i.e. least number
        // of possible placements. This is because the search tree is pruned and highly
        // constrained pieces cause more pruning, and also having the pieces that cause
        // many child nodes last causes the pruned subtrees to be bigger.
        // Pieces are tried in reverse order of the list, hence the reverse sort.
        this.pieces.sort(Comparator.comparing(Piece::getVariantCount).reversed());
        this.initialOccupied = initialOccupied;
        this.printSolution = printSolution;
        this.forkJoinPool = forkJoinPool;
    }

    @Override
    public void solve() {
        solutions.reset();
        tries.reset();
        start = Instant.now();

        if (!pieces.isEmpty()) {
            RecursiveSolverTask task = new RecursiveSolverTask(initialOccupied, pieces.size() - 1, -1, null);
            forkJoinPool.invoke(task);
        }
        printStats();
    }

    @SuppressWarnings("serial")
    private class RecursiveSolverTask extends RecursiveAction {

        private final long occupied;
        private final int pieceIndex;
        private final int parentVariant;
        private final RecursiveSolverTask parent;

        /**
         * Create a new recursive solver task.
         *
         * @param occupied the bit board of occupied positions so far.
         * @param pieceIndex the index of the piece to be placed by this task (counting
         * down).
         * @param parentVariant the variant placed by the parent task (for printing
         * solution).
         * @param parent the parent task (only used for printing solution)
         */
        public RecursiveSolverTask(long occupied, int pieceIndex, int parentVariant, RecursiveSolverTask parent) {
            this.occupied = occupied;
            this.pieceIndex = pieceIndex;
            this.parentVariant = parentVariant;
            this.parent = parent;
        }

        @Override
        protected void compute() {
            // The basic idea here is that we should place the next piece somewhere on the
            // board, trying each variant. When it can be placed we recursively try to place
            // the next piece, and so on, until the board is full or we run out of pieces
            // that can be placed.
            Piece piece = pieces.get(pieceIndex);

            // For optimization we do more work to prune the tree when we are not near the
            // leaves
            if (pieceIndex >= 2) {
                // Not last 2 pieces, do pruning and fork sub-tasks

                // First calculate how big the piece can be (holes left to cover minus min/max
                // size of remaining pieces)
                // The size pruning for previous piece makes sure at least one size of this
                // piece is acceptable
                List<Piece> remainingPieces = pieces.subList(0, pieceIndex);
                int minSize, maxSize;
                minSize = maxSize = BitBoard.X_SIZE * BitBoard.Y_SIZE - Long.bitCount(occupied);
                for (int i = 0; i < remainingPieces.size(); i++) {
                    Piece p = remainingPieces.get(i);
                    maxSize -= p.getMinSize();
                    minSize -= p.getMaxSize();
                }

                // Try all variants of this piece
                List<RecursiveSolverTask> children = new ArrayList<>(piece.getVariantCount());
                for (int i = 0; i < piece.getVariantCount(); i++) {
                    long bb = piece.getVariant(i);
                    tries.increment();
                    // Does it fit?
                    if ((bb & occupied) == 0) {
                        // Size pruning: Can't use too big/small variant so that there is to few/many
                        // empty positions left
                        int size = Long.bitCount(bb);
                        if (size >= minSize && size <= maxSize) {
                            // Can-fill pruning: Prune if it is impossible to fill board with remaining
                            // pieces.
                            long newOccupied = occupied | bb;
                            if (Piece.canFillAround(newOccupied, remainingPieces)) {
                                // Recursively place the next piece (possibly on a different thread)
                                RecursiveSolverTask child = new RecursiveSolverTask(newOccupied, pieceIndex - 1, i,
                                        this);
                                if (getSurplusQueuedTaskCount() <= 3) {
                                    child.fork();
                                    children.add(child);
                                } else {
                                    child.compute();
                                }
                            }
                        }
                    }
                }
                children.forEach(RecursiveSolverTask::join);

            } else {
                // Second to last piece, no pruning, no forking
                assert pieceIndex == 1;

                Piece lastPiece = pieces.get(0);
                // Try all variants of this piece
                for (int i = 0; i < piece.getVariantCount(); i++) {
                    long bb = piece.getVariant(i);
                    tries.increment();
                    // Does it fit?
                    if ((bb & occupied) == 0) {
                        // Recursively place the last piece
                        long newOccupied = occupied | bb;
                        if (printSolution) {
                            solveLastPrint(newOccupied, lastPiece, i);
                        } else {
                            solveLastCount(newOccupied, lastPiece);
                        }
                    }
                }

            }
        }

        /**
         * Special version of the algorithm for the last piece when only counting
         * solutions.
         *
         * @param occ bit board of occupied positions.
         * @param piece the piece to place.
         */
        private void solveLastCount(long occ, Piece piece) {
            // For the last piece we can do a simpler test:
            // We only need to find a variant that is identical to the unoccupied positions.
            // We can reduce the number of variants to try by only testing those with the
            // same first bit as the one we are looking for.
            long toCover = occ ^ BitBoard.FULL_BOARD;
            int firstBit = Long.numberOfTrailingZeros(toCover);
            for (int i = 0; i < piece.getVariantCountByFirstBit(firstBit); i++) {
                long bb = piece.getVariantByFirstBit(firstBit, i);
                if (bb == toCover) {
                    solutions.increment();
                    tries.add(i + 1);
                    return;
                }
            }
            // No solution found
            tries.add(piece.getVariantCountByFirstBit(firstBit));
        }

        /**
         * Special version of the algorithm for the last piece when printing solutions.
         *
         * @param occ bit board of occupied positions.
         * @param piece the piece to place.
         */
        private void solveLastPrint(long occ, Piece piece, int previ) {
            // We cannot easily do the same first bit trick here because printSolution needs
            // the full variant index. When printing speed isn't critical anyway.
            long holesToCover = occ ^ BitBoard.FULL_BOARD;
            for (int i = 0; i < piece.getVariantCount(); i++) {
                long bb = piece.getVariant(i);
                if (bb == holesToCover) {
                    solutions.increment();
                    tries.add(i + 1);
                    printSolution(piece, i, previ);
                    printStats();
                    return;
                }
            }
            // No solution found
            tries.add(piece.getVariantCount());
        }

        /**
         * Print the solution.
         * Walks the search tree of tasks up to the root to reconstruct the solution.
         * Since the last levels of the tree is not using task instances, values for
         * those are passed as parameters.
         *
         * @param lastPiece the last piece placed.
         * @param lastVariant the variant of the last piece.
         * @param prevVariant the variant of the previous piece.
         */
        private void printSolution(Piece lastPiece, int lastVariant, int prevVariant) {
            StringBuilder sb = new StringBuilder();
            for (int y = 0; y < BitBoard.Y_SIZE; y++) {
                sb.append(" ".repeat(BitBoard.X_SIZE)).append('\n');
            }
            drawPiece(sb, lastPiece, lastVariant);
            int variant = prevVariant;
            for (RecursiveSolverTask task = this; task != null; variant = task.parentVariant, task = task.parent) {
                Piece piece = pieces.get(task.pieceIndex);
                drawPiece(sb, piece, variant);
            }
            System.out.print(sb.toString());
        }

        /**
         * Draw ASCII art for a piece in a string builder.
         *
         * @param sb string builder, already filled with lines if characters for the
         * board.
         * @param piece the piece to draw.
         * @param variant the variant of the piece to draw.
         * @see #printSolution(Piece, int, int)
         */
        private void drawPiece(StringBuilder sb, Piece piece, int variant) {
            long bb = piece.getVariant(variant);
            char c = piece.getCharacter();
            for (int y = 0; y < BitBoard.Y_SIZE; y++) {
                for (int x = 0; x < BitBoard.X_SIZE; x++) {
                    if (BitBoard.isSet(bb, x, y)) {
                        int i = y * (BitBoard.X_SIZE + 1) + x;
                        if (sb.charAt(i) != ' ') {
                            System.out.printf("ERROR: Overlap at %d,%d between piece %c and %c\n", x, y,
                                    sb.charAt(i), c);
                        }
                        sb.setCharAt(i, c);
                    }
                }
            }
        }

    }

    private void printStats() {
        long t = tries.sum();
        Duration time = Duration.between(start, Instant.now());
        System.out.printf("Solutions: %d, tries: %d, time: %d.%03d s, speed: %d tries/s\n",
                solutions.sum(), t, time.getSeconds(), time.getNano() / 1000000,
                time.toMillis() > 0 ? t * 1000 / time.toMillis() : 0);
    }

}
