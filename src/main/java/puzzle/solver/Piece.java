// SPDX-License-Identifier: MIT
// Copyright 2023 Daniel Nilsson
package puzzle.solver;

import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * Represents a puzzle piece.
 * A piece can have more than one shape (in the physical game it is 3d and can
 * be rotated "out of plane" to have a alternative 2d shape).
 * A piece can be rotated and shifted, but not flipped.
 */
public class Piece {

    private final char character;
    private final long[] variants;
    private final long[][] variantsByFirstBit;
    private final int minSize;
    private final int maxSize;

    /**
     * Create a piece from one or more shape patterns.
     *
     * @param shapes strings that encodes the shapes of the piece. There must be at
     * least one. Each string should consist of rows separated by '\n'. Each row
     * should consist of spaces representing unused positions and some character
     * representing occupied positions. Every character must be the same in each and
     * every shape for the same piece and unique for that one piece. The same
     * character will later be used when printing the solution and for specifying
     * challenge patterns.
     */
    public Piece(String... shapes) {
        this(shapes[0].stripLeading().charAt(0),
                Stream.of(shapes)
                    .mapToLong(Piece::parse)
                    .flatMap(BitBoard::generateRotations)
                    .flatMap(BitBoard::generateYShifts)
                    .flatMap(BitBoard::generateXShifts)
                    .toArray());
    }

    private Piece(char character, long[] bitBords) {
        this.character = character;
        this.variants = bitBords;
        this.variantsByFirstBit = createTableByFirstBit(variants);
        IntSummaryStatistics stats = LongStream.of(bitBords)
            .mapToInt(Long::bitCount)
            .summaryStatistics();
        this.minSize = stats.getMin();
        this.maxSize = stats.getMax();
    }

    /**
     * Get the character representing this piece, used for printing and parsing.
     *
     * @return unique character for this piece.
     */
    public char getCharacter() {
        return character;
    }

    /**
     * Get the number of variants of this piece.
     * Each shape, rotation and shift is counted as a variant.
     *
     * @return the number of variants.
     */
    public int getVariantCount() {
        return variants.length;
    }

    /**
     * Get a specific variant of the piece as a bit board.
     * Each shape, rotation and shift is counted as a variant.
     *
     * @param i variant index (0...{@link #getVariantCount()} - 1)
     * @return bit board for the variant.
     */
    public long getVariant(int i) {
        return variants[i];
    }

    /**
     * Get the number of variants of this piece which have a given lowest bit set in
     * their bit board representation.
     *
     * @param bit the bit number (0...63)
     * @return the number of variants with the given lowest bit.
     */
    public int getVariantCountByFirstBit(int bit) {
        return variantsByFirstBit[bit].length;
    }

    /**
     * Get a specific variant of the piece as a bit board.
     * Selects among variants which have a given lowest bit set in their bit board
     * representation.
     *
     * @param bit the bit number (0...63)
     * @param i variant index (0...{@link #getVariantCountByFirstBit(int)} - 1)
     * @return bit board for the variant.
     */
    public long getVariantByFirstBit(int bit, int i) {
        return variantsByFirstBit[bit][i];
    }

    /**
     * Get the size of the smallest shape variant.
     *
     * @return the number of positions occupied by the smallest shape.
     */
    public int getMinSize() {
        return minSize;
    }

    /**
     * Get the size of the biggest shape variant.
     *
     * @return the number of positions occupied by the biggest shape.
     */
    public int getMaxSize() {
        return maxSize;
    }

    /**
     * Print all variants to {@link System#out} as ASCCI art.
     */
    public void dump() {
        LongStream.of(variants)
            .forEach(BitBoard::dump);
    }

    /**
     * Return a copy of this piece with any variant removed if it results in a board
     * that can obviously not be solved using the given pieces.
     * This method uses a heuristic to prune impossible variants quickly, it may
     * leave some variants that will never be used in a full solution, but it will
     * never remove a variant that is needed for some solution.
     * This is meant to speed up a full solver by eliminating some work without full
     * search.
     *
     * @param pieces list of pieces to use to fill the board (may include this
     * piece, which will be skipped)
     * @return a copy of this piece with (hopefully) fewer variants.
     */
    public Piece pruned(List<Piece> pieces) {
        List<Piece> piecesExceptThis = pieces.stream()
            .filter(Predicate.isEqual(this).negate())
            .toList();
        long[] prunedBitBoards = LongStream.of(variants)
            .filter(bb -> canFillAround(bb, piecesExceptThis))
            .toArray();
        return new Piece(character, prunedBitBoards);
    }

    /**
     * Check if there is any chance that the given list of pieces can by used to
     * fill the unoccupied positions of the given bit board.
     * This is just an heuristic that runs in linear time (as opposed to exponential
     * time for a full search).
     *
     * @param bb the bit board to check
     * @param pieces the pieces to use for the check
     * @return false if it can be proven that it is impossible to solve the board,
     * true if it may be possible to solve it.
     */
    public static boolean canFillAround(long bb, List<Piece> pieces) {
        // Check if there is any hole that no piece can cover.
        // Could also check if there is any piece that can't fit at all
        // (but that was too slow for the gain in pruning to be worth it)
        long acc = bb;
        for (int i = 0; i < pieces.size(); i++) {
            Piece piece = pieces.get(i);
            for (long pbb : piece.variants) {
                if ((pbb & bb) == 0) {
                    acc |= pbb;
                }
            }
            if (acc == BitBoard.FULL_BOARD) { // Moving this to the inner loop is slower
                return true;
            }
        }
        return false;
    }

    private static long parse(String form) {
        long bitBoard = 0;
        String[] rows = form.split("\n");
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                if (rows[y].charAt(x) != ' ') {
                    bitBoard |= BitBoard.getBit(x, y);
                }
            }
        }
        return bitBoard;
    }

    private static long[][] createTableByFirstBit(long[] bitBoards) {
        long[][] bbs = new long[65][]; // Allocate 65 so index 64 is valid in case Long.numberOfTrailingZeros(0L) is
                                       // used (avoids explicit check for that case)
        for (int i = 0; i < bbs.length; i++) {
            int bit = i;
            bbs[i] = LongStream.of(bitBoards)
                .filter(bb -> Long.numberOfTrailingZeros(bb) == bit)
                .toArray();
        }
        return bbs;
    }

}
