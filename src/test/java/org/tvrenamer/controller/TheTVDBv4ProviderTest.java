package org.tvrenamer.controller;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.tvrenamer.controller.tvdb.TvdbV4Client;
import org.tvrenamer.controller.tvdb.TvdbV4Transport;
import org.tvrenamer.controller.tvdb.TvdbV4Transport.TvdbHttpResponse;
import org.tvrenamer.model.Series;
import org.tvrenamer.model.ShowName;
import org.tvrenamer.model.ShowOption;
import org.tvrenamer.model.TVRenamerIOException;
import org.tvrenamer.model.UserPreferences;

public class TheTVDBv4ProviderTest {

    /** One episode in aired ("default") order; hasNext=false. */
    private static final String AIRED_EPISODES_BODY =
        "{\"data\":{\"episodes\":[{\"id\":\"501\",\"seasonNumber\":\"1\","
        + "\"number\":\"1\",\"name\":\"Pilot\",\"aired\":\"2019-01-01\"}]},\"links\":{}}";

    private static TvdbV4Client clientReturning(String searchBody) {
        TvdbV4Transport t = new TvdbV4Transport() {
            public TvdbHttpResponse post(String u, String b, java.util.Map<String,String> h) {
                return new TvdbHttpResponse(200, "{\"data\":{\"token\":\"tok\"}}");
            }
            public TvdbHttpResponse get(String u, java.util.Map<String,String> h) {
                return new TvdbHttpResponse(200, searchBody);
            }
        };
        return new TvdbV4Client(t, () -> "key");
    }

    /** Client whose GET responses are decided per-URL by the supplied responder. */
    private static TvdbV4Client clientForListing(Function<String, TvdbHttpResponse> getResponder) {
        TvdbV4Transport t = new TvdbV4Transport() {
            public TvdbHttpResponse post(String u, String b, java.util.Map<String,String> h) {
                return new TvdbHttpResponse(200, "{\"data\":{\"token\":\"tok\"}}");
            }
            public TvdbHttpResponse get(String u, java.util.Map<String,String> h) {
                return getResponder.apply(u);
            }
        };
        return new TvdbV4Client(t, () -> "key");
    }

    /** Run {@code body} with the DVD-order preference forced on, then restore it. */
    private static void withPreferDvd(ThrowingRunnable body) throws Exception {
        UserPreferences prefs = UserPreferences.getInstance();
        boolean original = prefs.isPreferDvdOrderIfPresent();
        prefs.setPreferDvdOrderIfPresent(true);
        try {
            body.run();
        } finally {
            prefs.setPreferDvdOrderIfPresent(original);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    @Test
    public void getShowOptionsPopulatesShowName() throws Exception {
        String body = "{\"data\":[{\"tvdb_id\":\"1001\",\"name\":\"Solar Drift\","
                    + "\"year\":\"2019\",\"aliases\":[\"SD\"]}]}";
        TheTVDBv4Provider provider = new TheTVDBv4Provider(clientReturning(body));
        ShowName sn = ShowName.mapShowName("solar drift");
        provider.getShowOptions(sn);
        List<ShowOption> opts = sn.getShowOptions();
        assertEquals(1, opts.size());
        assertEquals("1001", opts.get(0).getIdString());
    }

    @Test
    public void dvdErrorStatusFallsBackToAiredOrder() throws Exception {
        // Regression: v4 signals "no DVD ordering" with a non-200 error status
        // (not 200+empty). The dvd fetch throws; the provider must fall back to
        // aired ("default") order instead of failing the whole listing.
        TheTVDBv4Provider provider = new TheTVDBv4Provider(clientForListing(url -> {
            if (url.contains("/episodes/dvd")) {
                return new TvdbHttpResponse(404, "");
            }
            return new TvdbHttpResponse(200, AIRED_EPISODES_BODY);
        }));
        Series series = Series.createSeries(990201, "Fallback On Error");
        withPreferDvd(() -> provider.getSeriesListing(series));
        assertFalse(series.noEpisodes(),
            "aired-order episodes should be populated after the dvd fetch errored");
    }

    @Test
    public void dvdEmptyListFallsBackToAiredOrder() throws Exception {
        // The other "no DVD ordering" signal: 200 + an empty episode list.
        TheTVDBv4Provider provider = new TheTVDBv4Provider(clientForListing(url -> {
            if (url.contains("/episodes/dvd")) {
                return new TvdbHttpResponse(200, "{\"data\":{\"episodes\":[]},\"links\":{}}");
            }
            return new TvdbHttpResponse(200, AIRED_EPISODES_BODY);
        }));
        Series series = Series.createSeries(990202, "Fallback On Empty");
        withPreferDvd(() -> provider.getSeriesListing(series));
        assertFalse(series.noEpisodes(),
            "aired-order episodes should be populated after an empty dvd listing");
    }

    @Test
    public void defaultFetchFailurePropagates() throws Exception {
        // If the aired ("default") fetch itself fails there is nothing left to
        // fall back to, so the error must propagate rather than be swallowed.
        TheTVDBv4Provider provider = new TheTVDBv4Provider(
            clientForListing(url -> new TvdbHttpResponse(500, "")));
        Series series = Series.createSeries(990203, "Hard Failure");
        withPreferDvd(() ->
            assertThrows(TVRenamerIOException.class, () -> provider.getSeriesListing(series)));
    }
}
