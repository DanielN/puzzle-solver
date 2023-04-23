// SPDX-License-Identifier: MIT
// Copyright 2023 Daniel Nilsson
package puzzle.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;

public class BitBoardTest {

    /**
     * <pre>
     *  0
     *  0
     * 00
     * </pre>
     */
    private static final long J_PIECE_0 = BitBoard.getBit(1, 0)
            | BitBoard.getBit(1, 1)
            | BitBoard.getBit(0, 2) | BitBoard.getBit(1, 2);

    /**
     * <pre>
     * 0
     * 000
     * </pre>
     */
    private static final long J_PIECE_90 = BitBoard.getBit(0, 0)
            | BitBoard.getBit(0, 1) | BitBoard.getBit(1, 1) | BitBoard.getBit(2, 1);

    /**
     * <pre>
     * 00
     * 0
     * 0
     * </pre>
     */
    private static final long J_PIECE_180 = BitBoard.getBit(0, 0) | BitBoard.getBit(1, 0)
            | BitBoard.getBit(0, 1)
            | BitBoard.getBit(0, 2);

    /**
     * <pre>
     * 000
     *   0
     * </pre>
     */
    private static final long J_PIECE_270 = BitBoard.getBit(0, 0) | BitBoard.getBit(1, 0) | BitBoard.getBit(2, 0)
            | BitBoard.getBit(2, 1);

    @Test
    public void testGetBit() {
        long acc = 0;
        for (int y = 0; y < BitBoard.Y_SIZE; y++) {
            for (int x = 0; x < BitBoard.X_SIZE; x++) {
                long bitBoard = BitBoard.getBit(x, y);
                assertEquals(1, Long.bitCount(bitBoard), "bit count of getBit(%d, %d)".formatted(x, y));
                assertTrue((bitBoard & acc) == 0, "uniqueness of getBit(%d, %d)".formatted(x, y));
                acc |= bitBoard;
            }
        }
    }

    @Test
    public void testIsSet() {
        for (int y = 0; y < BitBoard.Y_SIZE; y++) {
            for (int x = 0; x < BitBoard.X_SIZE; x++) {
                long bitBoard = BitBoard.getBit(x, y);
                for (int yy = 0; yy < BitBoard.Y_SIZE; yy++) {
                    for (int xx = 0; xx < BitBoard.X_SIZE; xx++) {
                        assertEquals(xx == x && yy == y, BitBoard.isSet(bitBoard, xx, yy),
                                "isSet(getBit(%d, %d), %d, %d)".formatted(x, y, xx, yy));
                    }
                }
            }
        }
    }

    @Test
    public void testValid() {
        long invalid = ~0L;
        for (int y = 0; y < BitBoard.Y_SIZE; y++) {
            for (int x = 0; x < BitBoard.X_SIZE; x++) {
                long bitBoard = BitBoard.getBit(x, y);
                assertTrue(BitBoard.valid(bitBoard), "valid(getBit(%d, %d))".formatted(x, y));
                invalid &= ~bitBoard;
            }
        }
        while (invalid != 0) {
            long bitBoard = Long.lowestOneBit(invalid);
            assertFalse(BitBoard.valid(bitBoard), "valid(%X)".formatted(bitBoard));
            invalid ^= bitBoard;
        }
    }

    @Test
    public void testXSize() {
        long bitBoard = 0;
        int res = BitBoard.xSize(bitBoard);
        assertEquals(0, res, "xSize(0)");
        for (int y = 0; y < BitBoard.Y_SIZE; y++) {
            for (int x = 0; x < BitBoard.X_SIZE; x++) {
                bitBoard = BitBoard.getBit(x, y);
                res = BitBoard.xSize(bitBoard);
                assertEquals(x + 1, res, "xSize(getBit(%d, %d))".formatted(x, y));
            }
        }
    }

    @Test
    public void testYSize() {
        long bitBoard = 0;
        int res = BitBoard.ySize(bitBoard);
        assertEquals(0, res, "ySize(0)");
        for (int y = 0; y < BitBoard.Y_SIZE; y++) {
            for (int x = 0; x < BitBoard.X_SIZE; x++) {
                bitBoard = BitBoard.getBit(x, y);
                res = BitBoard.ySize(bitBoard);
                assertEquals(y + 1, res, "ySize(getBit(%d, %d))".formatted(x, y));
            }
        }
    }

    @Test
    public void testRotateRight() {
        long res = BitBoard.rotateRight(J_PIECE_0);
        assertEquals(J_PIECE_90, res, "J 0 rotated");
        res = BitBoard.rotateRight(J_PIECE_90);
        assertEquals(J_PIECE_180, res, "J 90 rotated");
        res = BitBoard.rotateRight(J_PIECE_180);
        assertEquals(J_PIECE_270, res, "J 180 rotated");
        res = BitBoard.rotateRight(J_PIECE_270);
        assertEquals(J_PIECE_0, res, "J 280 rotated");
    }

    @Test
    public void testGenerateRotations() {
        LongStream res = BitBoard.generateRotations(J_PIECE_0);
        assertNotNull(res, "generateRotations() return value");
        long[] rotations = res.toArray();
        assertEquals(4, rotations.length, "rotations.length");
        assertEquals(J_PIECE_0, rotations[0], "rotations[0]");
        assertEquals(J_PIECE_90, rotations[1], "rotations[1]");
        assertEquals(J_PIECE_180, rotations[2], "rotations[2]");
        assertEquals(J_PIECE_270, rotations[3], "rotations[3]");
    }

    @Test
    public void testXShift() {
        for (int y = 0; y < BitBoard.Y_SIZE; y++) {
            for (int x = 0; x < BitBoard.X_SIZE; x++) {
                long bitBoard = BitBoard.getBit(x, y);
                long res = BitBoard.xShift(bitBoard);
                if (x == BitBoard.X_SIZE - 1) {
                    assertFalse(BitBoard.valid(res), "valid(xShift(getBit(%d, %d)))".formatted(x, y));
                } else {
                    long expected = BitBoard.getBit(x + 1, y);
                    assertEquals(expected, res, "xShift(getBit(%d, %d))".formatted(x, y));
                }
            }
        }
    }

    @Test
    public void testGenerateXShifts() {
        LongStream res = BitBoard.generateXShifts(J_PIECE_0);
        assertNotNull(res, "generateXShifts() return value");
        long[] shifts = res.toArray();
        // J_PIECE_0 is 2 wide
        assertEquals(BitBoard.X_SIZE - 2 + 1, shifts.length, "shifts.length");
        assertEquals(J_PIECE_0, shifts[0], "shifts[0]");
        // Could test the rest, but that would require generating the expected bit
        // boards manually
        // (can't use xShift for that since that would be a tautology).
        assertEquals(BitBoard.X_SIZE, BitBoard.xSize(shifts[shifts.length - 1]), "xSize(shifts[last])");
    }

    @Test
    public void testYShift() {
        for (int y = 0; y < BitBoard.Y_SIZE; y++) {
            for (int x = 0; x < BitBoard.X_SIZE; x++) {
                long bitBoard = BitBoard.getBit(x, y);
                long res = BitBoard.yShift(bitBoard);
                if (y == BitBoard.Y_SIZE - 1) {
                    assertFalse(BitBoard.valid(res), "valid(yShift(getBit(%d, %d)))".formatted(x, y));
                } else {
                    long expected = BitBoard.getBit(x, y + 1);
                    assertEquals(expected, res, "yShift(getBit(%d, %d))".formatted(x, y));
                }
            }
        }
    }

    @Test
    public void testGenerateYShifts() {
        LongStream res = BitBoard.generateYShifts(J_PIECE_0);
        assertNotNull(res, "generateYShifts() return value");
        long[] shifts = res.toArray();
        // J_PIECE_0 is 3 high
        assertEquals(BitBoard.Y_SIZE - 3 + 1, shifts.length, "shifts.length");
        assertEquals(J_PIECE_0, shifts[0], "shifts[0]");
        assertEquals(BitBoard.Y_SIZE, BitBoard.ySize(shifts[shifts.length - 1]), "ySize(shifts[last])");
    }

    @Test
    public void testDump() {
        // Not really a true test, since nothing is verified.
        // You can visually inspect the pieces used in other test cases.
        System.out.println("J 0:");
        BitBoard.dump(J_PIECE_0);
        System.out.println("J 90:");
        BitBoard.dump(J_PIECE_90);
        System.out.println("J 180:");
        BitBoard.dump(J_PIECE_180);
        System.out.println("J 270:");
        BitBoard.dump(J_PIECE_270);
    }

}
