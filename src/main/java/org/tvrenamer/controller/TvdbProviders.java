package org.tvrenamer.controller;

import org.tvrenamer.model.EpisodeDataProviderType;
import org.tvrenamer.model.UserPreferences;

/** Returns the active EpisodeDataProvider based on the user preference. */
public final class TvdbProviders {

    private static final EpisodeDataProvider V1 = new TheTVDBLegacyProvider();
    private static final EpisodeDataProvider V4 = new TheTVDBv4Provider();

    private TvdbProviders() {}

    public static EpisodeDataProvider current() {
        EpisodeDataProviderType type =
            UserPreferences.getInstance().getEpisodeDataProvider();
        return (type == EpisodeDataProviderType.TVDB_V4) ? V4 : V1;
    }
}
