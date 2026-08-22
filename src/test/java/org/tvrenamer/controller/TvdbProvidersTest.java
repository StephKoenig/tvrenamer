package org.tvrenamer.controller;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.tvrenamer.model.EpisodeDataProviderType;
import org.tvrenamer.model.UserPreferences;

public class TvdbProvidersTest {
    @Test
    public void selectorFollowsPreference() {
        UserPreferences p = UserPreferences.getInstance();
        try {
            p.setEpisodeDataProvider(EpisodeDataProviderType.TVMAZE);
            assertTrue(TvdbProviders.current() instanceof TvMazeProvider);
            p.setEpisodeDataProvider(EpisodeDataProviderType.TVDB_V4);
            assertTrue(TvdbProviders.current() instanceof TheTVDBv4Provider);
        } finally {
            p.setEpisodeDataProvider(EpisodeDataProviderType.TVMAZE);
        }
    }
}
