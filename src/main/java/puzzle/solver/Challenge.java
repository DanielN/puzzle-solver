// SPDX-License-Identifier: MIT
// Copyright 2023 Daniel Nilsson
package puzzle.solver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A puzzle challenge, consisting of initially occupied positions and a list of
 * available pieces.
 */
public class Challenge {

    private final long occupied;
    private final List<Piece> availablePieces;

    /**
     * Create a challenge from a list of pieces and a text pattern.
     *
     * @param pieces list of all pieces, including those used in the pattern.
     * @param pattern a string of lines separated by '\n' representing the initial
     * board layout. Spaces represents empty positions and characters represents
     * positions occupied by pieces, the characters must match the used pieces.
     */
    public Challenge(List<Piece> pieces, String pattern) {
        List<Piece> piecesCopy = new ArrayList<>(pieces);
        occupied = parse(pattern, piecesCopy);
        availablePieces = Collections.unmodifiableList(piecesCopy);
    }

    /**
     * Get the bit board of initially occupied positions.
     *
     * @return bit board of occupied positions.
     */
    public long getOccupied() {
        return occupied;
    }

    /**
     * Get the list of available (unused) pieces.
     *
     * @return unmodifiable list of available pieces.
     */
    public List<Piece> getAvailablePieces() {
        return availablePieces;
    }

    private static long parse(String pattern, List<Piece> pieces) {
        long[] pieceBoards = new long[pieces.size()];
        long occupied = 0L;

        // Parse each piece into bit boards
        String[] rows = pattern.split("\n");
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                if (rows[y].charAt(x) != ' ') {
                    long bit = BitBoard.getBit(x, y);
                    int p = findPieceIndexByCharacter(pieces, rows[y].charAt(x));
                    occupied |= bit;
                    pieceBoards[p] |= bit;
                }
            }
        }

        // Find used pieces and remove from piece list
        // Backwards so remove doesn't change iteration
        for (int p = pieces.size() - 1; p >= 0; p--) {
            if (pieceBoards[p] != 0L) {
                Piece piece = pieces.remove(p);
                // Sanity check that piece shape is correct
                boolean found = false;
                for (int i = 0; i < piece.getVariantCount(); i++) {
                    long bb = piece.getVariant(i);
                    if (bb == pieceBoards[p]) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    throw new IllegalArgumentException(
                            "Piece #%d doesn't match pattern: %s".formatted(p, BitBoard.toString(pieceBoards[p])));
                }
            }
        }

        return occupied;
    }

    private static int findPieceIndexByCharacter(List<Piece> pieces, char c) {
        for (int p = 0; p < pieces.size(); p++) {
            if (pieces.get(p).getCharacter() == c) {
                return p;
            }
        }
        throw new IllegalArgumentException("No piece found for character '%c'".formatted(c));
    }

}
