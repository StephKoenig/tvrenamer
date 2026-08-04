package org.tvrenamer.controller;

import org.tvrenamer.model.DiscontinuedApiException;
import org.tvrenamer.model.Series;
import org.tvrenamer.model.ShowName;
import org.tvrenamer.model.TVRenamerIOException;

/** Abstraction over a TheTVDB API version. Implementations populate the shared models. */
public interface EpisodeDataProvider {
    void getShowOptions(ShowName showName) throws TVRenamerIOException, DiscontinuedApiException;
    void getSeriesListing(Series series) throws TVRenamerIOException;
}
