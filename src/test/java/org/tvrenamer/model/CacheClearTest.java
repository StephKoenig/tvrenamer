package org.tvrenamer.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tests for the static cache-clear methods on {@link Series} and {@link ShowName}.
 * These are used on a provider switch (e.g. TheTVDB to TVMaze) since the two
 * providers use different id namespaces, so cached id-&gt;Series and
 * queryString-&gt;Show mappings from one provider must not leak into the other.
 */
public class CacheClearTest {

    // Use a high, unique id to avoid colliding with other tests' KNOWN_SERIES entries.
    private static final int UNIQUE_ID = 970001;

    @Test
    public void clearKnownSeriesAllowsSameIdDifferentName() {
        Series.createSeries(UNIQUE_ID, "Solar Drift");
        // Same id + different name would normally throw (id-collision guard).
        assertThrows(IllegalArgumentException.class,
            () -> Series.createSeries(UNIQUE_ID, "Westmark Academy"));
        Series.clearKnownSeries();
        // After clearing, the id is free to be re-created with a different name.
        Series reused = Series.createSeries(UNIQUE_ID, "Westmark Academy");
        assertEquals("Westmark Academy", reused.getName());
    }

    @Test
    public void clearAllQueryCacheDoesNotThrow() {
        ShowName.mapShowName("solar drift");
        ShowName.clearAllQueryCache();
        // A fresh mapping after clear returns a usable ShowName.
        assertNotNull(ShowName.mapShowName("solar drift"));
    }
}
