package org.tvrenamer.model;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tvrenamer.controller.UserPreferencesPersistence;

public class ProviderPreferencesTest {

    @AfterEach
    public void resetProviderPreferences() {
        // Prevent cross-test pollution of the shared preferences singleton.
        UserPreferences p = UserPreferences.getInstance();
        p.setEpisodeDataProvider(EpisodeDataProviderType.TVDB_V1);
        p.setTvdbV4ApiKey("");
    }

    @Test
    public void defaultsToV1AndEmptyKey() {
        // Build a fresh instance from empty parsed-XML inputs, rather than using
        // UserPreferences.getInstance(), which lazily loads the machine's real
        // ~/.tvrenamer/prefs.xml and would reflect whatever the user last saved
        // (e.g. TVDB_V4) instead of the constructor/parse default asserted here.
        Map<String, String> emptyScalars = new HashMap<>();
        List<String> emptyKeywords = List.of();
        Map<String, String> emptyNameOverrides = new HashMap<>();
        Map<String, String> emptyDisambigOverrides = new HashMap<>();
        UserPreferences defaults = UserPreferences.fromParsedXml(
            emptyScalars, emptyKeywords, emptyNameOverrides, emptyDisambigOverrides);

        // Default must be the keyless v1 provider.
        assertEquals(EpisodeDataProviderType.TVDB_V1, defaults.getEpisodeDataProvider());
        assertEquals("", defaults.getTvdbV4ApiKey());
    }

    @Test
    public void enumFromStringIsCaseInsensitive() {
        assertEquals(EpisodeDataProviderType.TVDB_V4,
                     EpisodeDataProviderType.fromString("tvdb_v4"));
        assertNull(EpisodeDataProviderType.fromString("nonsense"));
    }

    @Test
    public void persistenceRoundTrip(@TempDir Path dir) {
        Path file = dir.resolve("prefs.xml");
        UserPreferences p = UserPreferences.getInstance();
        p.setEpisodeDataProvider(EpisodeDataProviderType.TVDB_V4);
        p.setTvdbV4ApiKey("test-key-1234");
        UserPreferencesPersistence.persist(p, file);

        UserPreferences read = UserPreferencesPersistence.retrieve(file);
        assertEquals(EpisodeDataProviderType.TVDB_V4, read.getEpisodeDataProvider());
        assertEquals("test-key-1234", read.getTvdbV4ApiKey());
    }
}
