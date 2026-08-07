package org.tvrenamer.controller;

import java.util.ArrayList;
import java.util.List;
import org.tvrenamer.controller.tvdb.JdkHttpTransport;
import org.tvrenamer.controller.tvdb.TvdbV4Client;
import org.tvrenamer.controller.tvdb.V4Parser;
import org.tvrenamer.controller.tvdb.V4Parser.V4EpisodesPage;
import org.tvrenamer.controller.tvdb.V4Parser.V4SeriesResult;
import org.tvrenamer.model.EpisodeInfo;
import org.tvrenamer.model.Series;
import org.tvrenamer.model.ShowName;
import org.tvrenamer.model.TVRenamerIOException;
import org.tvrenamer.model.UserPreferences;

/** v4 provider: uses the v4 client + parser to populate the shared models. */
public class TheTVDBv4Provider implements EpisodeDataProvider {

    private static final int MAX_PAGES = 20; // safety cap: 20 * 500 episodes

    private final TvdbV4Client client;

    public TheTVDBv4Provider() {
        this(new TvdbV4Client(new JdkHttpTransport(),
            () -> UserPreferences.getInstance().getTvdbV4ApiKey()));
    }

    // Test seam.
    public TheTVDBv4Provider(TvdbV4Client client) {
        this.client = client;
    }

    @Override
    public void getShowOptions(ShowName showName) throws TVRenamerIOException {
        showName.clearShowOptions();
        String json = client.searchSeriesJson(showName.getQueryString());
        for (V4SeriesResult r : V4Parser.parseSearchSeries(json)) {
            showName.addShowOption(r.tvdbId(), r.name(), r.year(), r.aliases());
        }
    }

    @Override
    public void getSeriesListing(Series series) throws TVRenamerIOException {
        boolean preferDvd = UserPreferences.getInstance().isPreferDvdOrderIfPresent();
        List<EpisodeInfo> episodes;
        if (preferDvd) {
            // A series without DVD ordering may be signalled either as 200 + an
            // empty list OR as a non-200 error status (which fetchAll surfaces as
            // a TVRenamerIOException). Treat both the same: fall back to aired
            // order. Only a failure of the aired ("default") fetch is fatal.
            List<EpisodeInfo> dvdEpisodes = null;
            try {
                dvdEpisodes = fetchAll(series.getId(), "dvd");
            } catch (TVRenamerIOException e) {
                dvdEpisodes = null;
            }
            episodes = (dvdEpisodes != null && !dvdEpisodes.isEmpty())
                ? dvdEpisodes
                : fetchAll(series.getId(), "default");
        } else {
            episodes = fetchAll(series.getId(), "default");
        }
        // v4 ordering is baked into the chosen season-type; no per-episode DVD fallback.
        series.setPreferDvd(false);
        series.addEpisodeInfos(episodes.toArray(new EpisodeInfo[0]));
        series.listingsSucceeded();
    }

    private List<EpisodeInfo> fetchAll(int seriesId, String seasonType)
        throws TVRenamerIOException {
        List<EpisodeInfo> all = new ArrayList<>();
        int page = 0;
        boolean more = true;
        while (more && page < MAX_PAGES) {
            String json = client.episodesJson(seriesId, seasonType, null, page);
            V4EpisodesPage p = V4Parser.parseEpisodes(json);
            all.addAll(p.episodes());
            more = p.hasNext();
            page++;
        }
        return all;
    }
}
