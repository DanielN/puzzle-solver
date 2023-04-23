// SPDX-License-Identifier: MIT
// Copyright 2023 Daniel Nilsson
package puzzle.solver;

import java.util.Arrays;
import java.util.List;

/**
 * Factory for puzzle pieces.
 */
public class PieceFactory {

    /**
     * Get a list of pieces with impossible variant pruned.
     *
     * @return list of pieces.
     */
    public static List<Piece> getPiecesPruned() {
        List<Piece> pieces = PieceFactory.getPieces();
        return pieces.stream()
            .map(p -> p.pruned(pieces))
            .toList();

    }

    /**
     * Get a list of pieces.
     *
     * @return list of pieces.
     */
    public static List<Piece> getPieces() {
        return Arrays.asList(new Piece("""
                00
                 0
                00
                """, """
                0
                0
                00
                """), new Piece("""
                1
                11
                11
                """, """
                 1
                11
                 1
                """), new Piece("""
                 2
                22
                22
                """, """
                22
                2
                2
                """), new Piece("""
                33
                3
                33
                """, """
                 3
                33
                 3
                """), new Piece("""
                 4
                 4
                44
                44
                """, """
                44
                4
                4
                4
                """), new Piece("""
                5
                55
                5
                55
                """, """
                 5
                55
                 5
                 5
                """), new Piece("""
                6
                6
                6
                66
                """, """
                66
                 6
                 6
                66
                """), new Piece("""
                77
                77
                 7
                 7
                """, """
                7
                77
                7
                7
                """), new Piece("""
                88
                8
                88
                8
                """, """
                88
                 8
                 8
                 8
                """), new Piece("""
                9
                99
                99
                9
                """, """
                 9
                99
                 9
                 9
                """));
    }

}
