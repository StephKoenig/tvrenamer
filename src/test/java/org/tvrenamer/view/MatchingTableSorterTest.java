package org.tvrenamer.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the ordering used when the Matching tables are sorted by a column
 * header. The rows are held as cell arrays (column 0 is the status icon column,
 * columns 1 and 2 hold the values), so the comparator sorts on a chosen index.
 */
public class MatchingTableSorterTest {

    private static List<String[]> rows(String... values) {
        List<String[]> list = new ArrayList<>();
        for (String v : values) {
            list.add(new String[] { "", v, "x" });
        }
        return list;
    }

    private static String[] columnOne(List<String[]> rows) {
        String[] out = new String[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            out[i] = rows.get(i)[1];
        }
        return out;
    }

    @Test
    @DisplayName("Ascending sort orders a column case-insensitively")
    public void ascendingIgnoresCase() {
        List<String[]> data = rows("banana", "Apple", "cherry");

        data.sort(MatchingTableSorter.byColumn(1, true));

        assertArrayEquals(new String[] { "Apple", "banana", "cherry" }, columnOne(data));
    }

    @Test
    @DisplayName("Descending sort reverses the ascending order")
    public void descendingReverses() {
        List<String[]> data = rows("banana", "Apple", "cherry");

        data.sort(MatchingTableSorter.byColumn(1, false));

        assertArrayEquals(new String[] { "cherry", "banana", "Apple" }, columnOne(data));
    }

    @Test
    @DisplayName("Null and missing cells are treated as empty rather than throwing")
    public void nullCellsSortAsEmpty() {
        List<String[]> data = new ArrayList<>();
        data.add(new String[] { "", "beta", "x" });
        data.add(new String[] { "", null, "x" });
        data.add(new String[] { "" }); // shorter row: column 1 absent

        data.sort(MatchingTableSorter.byColumn(1, true));

        // Both empty-ish rows sort before "beta"; the populated row goes last.
        assertEquals("beta", data.get(2)[1]);
    }

    @Test
    @DisplayName("Rows equal ignoring case fall back to a deterministic order")
    public void caseOnlyTiesAreDeterministic() {
        List<String[]> data = rows("apple", "APPLE", "Apple");

        Comparator<String[]> cmp = MatchingTableSorter.byColumn(1, true);
        data.sort(cmp);
        String[] first = columnOne(data);

        List<String[]> again = rows("Apple", "apple", "APPLE");
        again.sort(cmp);

        assertArrayEquals(first, columnOne(again));
    }
}
