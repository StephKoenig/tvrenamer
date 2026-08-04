package org.tvrenamer.controller;

import org.tvrenamer.model.DiscontinuedApiException;
import org.tvrenamer.model.Series;
import org.tvrenamer.model.ShowName;
import org.tvrenamer.model.TVRenamerIOException;

/** v1 provider: delegates to the existing static TheTVDBProvider (unchanged). */
public class TheTVDBLegacyProvider implements EpisodeDataProvider {
    @Override
    public void getShowOptions(ShowName showName)
        throws TVRenamerIOException, DiscontinuedApiException {
        TheTVDBProvider.getShowOptions(showName);
    }

    @Override
    public void getSeriesListing(Series series) throws TVRenamerIOException {
        TheTVDBProvider.getSeriesListing(series);
    }
}
