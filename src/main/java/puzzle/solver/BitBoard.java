// SPDX-License-Identifier: MIT
// Copyright 2023 Daniel Nilsson
package puzzle.solver;

import java.util.stream.LongStream;

/**
 * Encodes the board of the puzzle in a long, one bit per position.
 */
public class BitBoard {

    // Size of the board (must fit in 64 bits with the margin, see below)
    public static final int X_SIZE = 10;
    public static final int Y_SIZE = 5;

    // The shift is one larger than the X size to leave a margin
    // to help detect when a piece is shifted of the edge.
    private static final int Y_SHIFT = X_SIZE + 1;

    /**
     * A bit that is outside the valid bits for the board.
     * Setting this bit makes the bit board invalid.
     */
    private static final long INVALID_BIT = 1L << 63;

    /**
     * Bit board mask for the top row of the board.
     */
    public static final long FIRST_ROW;
    /**
     * Bit board mask for the bottom row of the board.
     */
    public static final long LAST_ROW;
    /**
     * Bit board mask for the rightmost column of the board.
     */
    public static final long LAST_COLUMN;
    /**
     * Bit board mask for all valid position of the board.
     */
    public static final long FULL_BOARD;

    static {
        long board = 0;
        for (int x = 0; x < X_SIZE; x++) {
            board |= getBit(x, 0);
        }
        FIRST_ROW = board;
        LAST_ROW = board << ((Y_SIZE - 1) * Y_SHIFT);

        board = 0;
        for (int y = 0; y < Y_SIZE; y++) {
            board |= getBit(X_SIZE - 1, y);
        }
        LAST_COLUMN = board;

        board = 0;
        for (int y = 0; y < Y_SIZE; y++) {
            board |= FIRST_ROW << (y * Y_SHIFT);
        }
        FULL_BOARD = board;
    }

    /**
     * Get a bit board with one bit set at the specified position.
     *
     * @param x column (0...X_SIZE-1)
     * @param y row (0...Y_SIZE-1)
     * @return bit board with one bit set
     */
    public static long getBit(int x, int y) {
        checkRange(x, y);
        return 1L << (y * Y_SHIFT + x);
    }

    /**
     * Check if a bit board has the bit set at the specified position.
     *
     * @param bitBoard the bit board to check
     * @param x column (0...X_SIZE-1)
     * @param y row (0...Y_SIZE-1)
     * @return true if bitBoard has the bit at (x, y) set, false if not
     */
    public static boolean isSet(long bitBoard, int x, int y) {
        checkRange(x, y);
        return (bitBoard & getBit(x, y)) != 0;
    }

    /**
     * Check if the bit board is valid.
     * I.e. it has no bits set outside the valid board positions.
     *
     * @param bitBoard the bit board to check
     * @return true if bitBoard is valid, false if any invalid bit is set
     */
    public static boolean valid(long bitBoard) {
        return (bitBoard & ~FULL_BOARD) == 0;
    }

    /**
     * Get the number of columns from the left to the rightmost column with a bit
     * set.
     * I.e. if a piece is left justified on the board then return the width of the
     * piece.
     *
     * @param bitBoard the bit board to check
     * @return X coordinate of the rightmost set bit plus one, zero if bit board is
     * empty.
     */
    public static int xSize(long bitBoard) {
        // OR all rows together and find index of highest set bit
        long acc = 0;
        for (int y = 0; y < Y_SIZE; y++) {
            acc |= bitBoard >>> (y * Y_SHIFT);
        }
        acc &= FIRST_ROW;
        return 64 - Long.numberOfLeadingZeros(acc);
    }

    /**
     * Get the number of rows from the top to the bottommost column with a bit set.
     * I.e. if a piece is top justified on the board then return the height of the
     * piece.
     *
     * @param bitBoard the bit board to check
     * @return Y coordinate of the bottommost set bit plus one, zero if bit board is
     * empty.
     */
    public static int ySize(long bitBoard) {
        // Find index of highest set bit and divide by row length, rounding up
        return (64 - Long.numberOfLeadingZeros(bitBoard) + Y_SHIFT - 1) / Y_SHIFT;
    }

    /**
     * Rotate a bit board 90 degrees to the right (clockwise).
     * The input bit board must be adjusted to the top left and not be
     * too large to fit on the board when rotated.
     * The resulting bit board is adjusted to the top left.
     *
     * @param bitBoard the bit board to rotate, should be adjusted to to left.
     * @return a rotated bit board
     */
    public static long rotateRight(long bitBoard) {
        long newBoard = 0;
        int xSize = xSize(bitBoard);
        int ySize = ySize(bitBoard);
        for (int y = 0; y < ySize; y++) {
            for (int x = 0; x < xSize; x++) {
                if (isSet(bitBoard, x, y)) {
                    newBoard |= getBit(ySize - y - 1, x);
                }
            }
        }
        return newBoard;
    }

    /**
     * Create a stream of of all four rotation of a bit board.
     * The input bit board must be adjusted to the top left and not be
     * too large to fit on the board when rotated.
     * The resulting bit boards are adjusted to the top left.
     *
     * @param bitBoard the bit board to rotate
     * @return a stream with the for rotations
     */
    public static LongStream generateRotations(long bitBoard) {
        return LongStream.iterate(bitBoard, BitBoard::rotateRight).limit(4);
    }

    /**
     * Shift a bit board one step to the right.
     *
     * @param bitBoard the bit board to shift
     * @return the bit board shifted one step to the right, will be invalid if the
     * input touched the right side of the board
     */
    public static long xShift(long bitBoard) {
        return bitBoard << 1;
    }

    /**
     * Create a stream of copies of a bit board, each one shifted one step
     * further to the right. The last bit board will touch the right edge of the
     * board.
     *
     * @param bitBoard the bit board to shift
     * @return a stream if shifted copies if the bit board, starting with the
     * original bit board
     */
    public static LongStream generateXShifts(long bitBoard) {
        return LongStream.iterate(bitBoard, BitBoard::valid, BitBoard::xShift);
    }

    /**
     * Shift a bit board one step down.
     *
     * @param bitBoard the bit board to shift
     * @return the bit board shifted one step down, will be invalid if the input
     * touched the bottom of the board
     */
    public static long yShift(long bitBoard) {
        if ((bitBoard & LAST_ROW) != 0) {
            // Make sure result is flagged invalid even if the offending bit gets shifted
            // out (there is not enough bits to have a margin for the last bit of the board)
            return (bitBoard << Y_SHIFT) | INVALID_BIT;
        }
        return bitBoard << Y_SHIFT;
    }

    /**
     * Create a stream of copies of a bit board, each one shifted one step
     * further down. The last bit board will touch the bottom edge of the
     * board.
     *
     * @param bitBoard the bit board to shift
     * @return a stream if shifted copies if the bit board, starting with the
     * original bit board
     */
    public static LongStream generateYShifts(long bitBoard) {
        return LongStream.iterate(bitBoard, BitBoard::valid, BitBoard::yShift);
    }

    /**
     * Create a simple one line representation of a bit board for debugging.
     *
     * @param bitBoard a bit board
     * @return a one line string representation of the bit board
     */
    public static String toString(long bitBoard) {
        StringBuilder sb = new StringBuilder("|");
        for (int y = 0; y < Y_SIZE; y++) {
            for (int x = 0; x < X_SIZE; x++) {
                sb.append(isSet(bitBoard, x, y) ? '0' : ' ');
            }
            sb.append('|');
        }
        return sb.toString();
    }

    /**
     * Print an ASCII art representation of a bit board to {@link System#out}.
     *
     * @param bitBoard a bit board
     */
    public static void dump(long bitBoard) {
        System.out.println("+" + "-".repeat(X_SIZE) + "+");
        for (int y = 0; y < Y_SIZE; y++) {
            System.out.print('|');
            for (int x = 0; x < X_SIZE; x++) {
                System.out.print(isSet(bitBoard, x, y) ? '0' : ' ');
            }
            System.out.println('|');
        }
        System.out.println("+" + "-".repeat(X_SIZE) + "+");
    }

    private static void checkRange(int x, int y) {
        if (x < 0 || x >= X_SIZE || y < 0 || y >= Y_SIZE) {
            throw new IllegalArgumentException("Coordinate %d,%d is out of range".formatted(x, y));
        }
    }

}
