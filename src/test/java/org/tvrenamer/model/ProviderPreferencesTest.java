package org.tvrenamer.model;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
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
        UserPreferences p = UserPreferences.getInstance();
        // Default must be the keyless v1 provider.
        assertEquals(EpisodeDataProviderType.TVDB_V1, p.getEpisodeDataProvider());
        assertEquals("", p.getTvdbV4ApiKey());
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
