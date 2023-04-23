// SPDX-License-Identifier: MIT
// Copyright 2023 Daniel Nilsson
package puzzle.solver;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.LongAdder;

/**
 * Solve puzzle by trying to fill one position at a time, trying every variant
 * of every piece that can fill the next empty position.
 * This solver can only count total number of solutions.
 */
public class SolverByPosition implements Solver {

    private final List<Piece> pieces;
    private final ForkJoinPool forkJoinPool;
    private final long[][] pieceMasksByPos;
    private final int maxPos;
    private final long solvedMask;

    private final LongAdder solutions = new LongAdder();
//    private final LongAdder tries = new LongAdder();

    private Instant start;

    /**
     * Create a new solver.
     *
     * @param pieces the available pieces.
     * @param forkJoinPool thread pool to use for the search.
     */
    public SolverByPosition(List<Piece> pieces, ForkJoinPool forkJoinPool) {
        this.pieces = pieces;
        this.forkJoinPool = forkJoinPool;
        // We use a trick here to keep track of what pieces are used in the high bits of
        // the occupied mask.
        // This works because the way we encode the board into bits leaves exactly one
        // bit for each piece.
        // We check this assumption here, just in case...
        if (pieces.size() > Long.numberOfLeadingZeros(BitBoard.FULL_BOARD)) {
            throw new IllegalArgumentException("Too many pieces or to few unused mask bits");
        }
        // Go through all variants of all pieces and create a table of masks indexed by
        // the position of the first set bit in each variant bit board.
        // The masks contain the variant bit board plus the bit that encodes the piece.
        pieceMasksByPos = new long[64][];
        int max = 0;
        for (int pos = 0; pos < pieceMasksByPos.length; pos++) {
            List<Long> masks = new ArrayList<>();
            for (int p = 0; p < pieces.size(); p++) {
                Piece piece = pieces.get(p);
                for (int i = 0; i < piece.getVariantCountByFirstBit(pos); i++) {
                    long mask = piece.getVariantByFirstBit(pos, i);
                    mask |= 1L << (63 - p);
                    masks.add(mask);
                }
            }
            pieceMasksByPos[pos] = masks.stream()
                .mapToLong(Long::longValue)
                .toArray();
            if (!masks.isEmpty()) {
                max = pos;
            }
        }
        // Highest position that actually contains any variants.
        maxPos = max;
        // We have a solution when the board is full and all pieces are used
        long allPiecesMask = (1L << 63) >> (pieces.size() - 1);
        solvedMask = BitBoard.FULL_BOARD | allPiecesMask;
    }

    @Override
    public void solve() {
        solutions.reset();
//        tries.reset();
        start = Instant.now();

        RecursiveSolverTask task = new RecursiveSolverTask(0L, pieces.size() - 1);
        forkJoinPool.invoke(task);

        printStats();
    }

    @SuppressWarnings("serial")
    private class RecursiveSolverTask extends RecursiveAction {

        private final long occupied;
        private final int level;

        /**
         * Create a new recursive solver task.
         *
         * @param occupied the bit board of occupied positions so far.
         * @param level the level in the search tree, counting down to 0 at the leaves.
         */
        public RecursiveSolverTask(long occupied, int level) {
            this.occupied = occupied;
            this.level = level;
        }

        @Override
        protected void compute() {
            // The basic idea here is that we should place a piece that occupies the first
            // empty position on the board, tying each variant of each remaining piece.
            // When one that fits is found we recursively try to fill the next empty
            // position until the board is full or we run out of pieces that can be placed.
            int firstEmpty = Long.numberOfTrailingZeros(occupied ^ BitBoard.FULL_BOARD);

            if (level == 1) {
                // Second to last level, no pruning or forking

                // Try each variant for the first empty position
                for (long mask : pieceMasksByPos[firstEmpty]) {
//                    tries.increment();

                    // Check if it fits and at the same time if the piece is unused
                    if ((mask & occupied) == 0) {
                        // Recursively solve the last level
                        long newOccupied = occupied | mask;
                        solveLast(newOccupied);
                    }
                }
            } else {
                // Earlier levels, do pruning and forking

                List<RecursiveSolverTask> children = new ArrayList<>(pieceMasksByPos[firstEmpty].length);
                // Try each variant for the first empty position
                for (long mask : pieceMasksByPos[firstEmpty]) {
//                    tries.increment();
                    // Check if it fits and at the same time if the piece is unused
                    if ((mask & occupied) == 0) {
                        long newOccupied = occupied | mask;
                        // Can-fill pruning: Prune if it is impossible to fill board with remaining
                        // pieces.
                        if (canFillAround(newOccupied)) {
                            // Recursively solve the next remaining board (possibly on a different thread)
                            RecursiveSolverTask child = new RecursiveSolverTask(newOccupied, level - 1);
                            if (getSurplusQueuedTaskCount() <= 3) {
                                child.fork();
                                children.add(child);
                            } else {
                                child.compute();
                            }
                        }
                    }
                }
                children.forEach(RecursiveSolverTask::join);
            }
        }

    }

    /**
     * Special version of the algorithm for the last level.
     *
     * @param occupied bit board of occupied positions.
     */
    private void solveLast(long occupied) {
        // For the last piece we can do a simpler test:
        // We only need to find a variant that is identical to the unoccupied positions.
        // This also includes the piece mask bit for the unused piece.
        int firstEmpty = Long.numberOfTrailingZeros(occupied ^ BitBoard.FULL_BOARD);
        long toCover = occupied ^ solvedMask;
//        int t = 0;
        // Try each variant for the first empty position
        for (long mask : pieceMasksByPos[firstEmpty]) {
//            t++;
            // Check if it fits and at the same time if it is the unused piece
            if (mask == toCover) {
                solutions.increment();
                break;
            }
        }
//        tries.add(t);
    }

    /**
     * This is a special variant of {@link Piece#canFillAround(long, List)} that
     * works with the masks that also includes the piece identification bits.
     *
     * @param occupied the bit board to check
     * @return false if it can be proven that it is impossible to solve the board,
     * true if it may be possible to solve it.
     */
    private boolean canFillAround(long occupied) {
        // Check if there is any hole that no piece can cover.
        // Thanks to the piece bits this also checks that there is no piece that can't
        // be placed.
        int firstEmpty = Long.numberOfTrailingZeros(occupied ^ BitBoard.FULL_BOARD);
        long acc = occupied;
        for (int pos = firstEmpty; pos <= maxPos; pos++) {
            for (long mask : pieceMasksByPos[pos]) {
                if ((mask & occupied) == 0) {
                    acc |= mask;
                }
            }
            if (acc == solvedMask) {
                return true;
            }
        }
        return false;
    }

    private void printStats() {
//        long t = tries.sum();
        long t = 1830481941; // Tries count for the current algorithm, recount if it code is changed.
        Duration time = Duration.between(start, Instant.now());
        System.out.printf("Solutions: %d, tries: %d, time: %d.%03d s, speed: %d tries/s\n",
                solutions.sum(), t, time.getSeconds(), time.getNano() / 1000000,
                time.toMillis() > 0 ? t * 1000 / time.toMillis() : 0);
    }

}
