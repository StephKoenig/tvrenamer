package org.tvrenamer.view;

import java.util.Comparator;

/**
 * Ordering for the Preferences "Matching" tables (Overrides and
 * Disambiguations) when the user sorts by clicking a column header.
 *
 * Rows are represented as their cell values, where column 0 is the narrow
 * status-icon column and columns 1 and 2 hold the two editable values. Kept
 * free of SWT types so the ordering can be unit-tested without a Display.
 */
final class MatchingTableSorter {

    private MatchingTableSorter() {
        // static helper only
    }

    /**
     * Build a comparator over row cell arrays.
     *
     * @param column    index of the cell to order by
     * @param ascending true for A-Z, false for Z-A
     * @return a null-safe, case-insensitive comparator
     */
    static Comparator<String[]> byColumn(
        final int column,
        final boolean ascending
    ) {
        Comparator<String[]> cmp = (a, b) -> {
            String left = cell(a, column);
            String right = cell(b, column);
            int result = left.compareToIgnoreCase(right);
            if (result == 0) {
                // Values differing only by case would otherwise order
                // unpredictably; fall back to an exact compare so repeated
                // sorts of the same data always agree.
                result = left.compareTo(right);
            }
            return result;
        };
        return ascending ? cmp : cmp.reversed();
    }

    /**
     * Read a cell defensively: rows may be shorter than expected and individual
     * cells may be null, neither of which should break sorting.
     */
    private static String cell(final String[] row, final int column) {
        if (row == null || column < 0 || column >= row.length) {
            return "";
        }
        String value = row[column];
        return (value == null) ? "" : value;
    }
}
