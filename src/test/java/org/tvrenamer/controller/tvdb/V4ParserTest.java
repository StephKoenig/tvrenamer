package org.tvrenamer.controller.tvdb;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.tvrenamer.controller.tvdb.V4Parser.V4EpisodesPage;
import org.tvrenamer.controller.tvdb.V4Parser.V4SeriesResult;
import org.tvrenamer.model.EpisodeInfo;

public class V4ParserTest {

    @Test
    public void parsesSearchResults() {
        String json = "{\"status\":\"success\",\"data\":[";
        json += "{\"objectID\":\"series-1001\",\"tvdb_id\":\"1001\",\"name\":\"Solar Drift\","
              + "\"year\":\"2019\",\"aliases\":[\"Solar Drift Redux\",\"SD\"]},";
        json += "{\"objectID\":\"series-1002\",\"tvdb_id\":\"1002\",\"name\":\"Westmark Academy\","
              + "\"aliases\":[]}]}";
        List<V4SeriesResult> r = V4Parser.parseSearchSeries(json);
        assertEquals(2, r.size());
        assertEquals("1001", r.get(0).tvdbId());
        assertEquals("Solar Drift", r.get(0).name());
        assertEquals(Integer.valueOf(2019), r.get(0).year());
        assertEquals(List.of("Solar Drift Redux", "SD"), r.get(0).aliases());
        assertNull(r.get(1).year(), "missing year -> null");
    }

    @Test
    public void emptySearchYieldsEmptyList() {
        assertTrue(V4Parser.parseSearchSeries("{\"status\":\"success\",\"data\":[]}").isEmpty());
    }

    @Test
    public void parsesEpisodesAndPagination() {
        String json = "{\"status\":\"success\",\"data\":{\"episodes\":[";
        json += "{\"id\":9001,\"seriesId\":1001,\"seasonNumber\":1,\"number\":8,"
              + "\"name\":\"The Quiet Ones\",\"aired\":\"2019-06-30\"},";
        json += "{\"id\":9002,\"seriesId\":1001,\"seasonNumber\":1,\"number\":9,"
              + "\"name\":null,\"aired\":\"2019-07-07\"}]},"
              + "\"links\":{\"next\":\"https://api4.thetvdb.com/v4/x?page=1\"}}";
        V4EpisodesPage page = V4Parser.parseEpisodes(json);
        assertEquals(2, page.episodes().size());
        assertTrue(page.hasNext());
        EpisodeInfo e = page.episodes().get(0);
        assertEquals("1", e.seasonNumber);
        assertEquals("8", e.episodeNumber);
        assertEquals("The Quiet Ones", e.episodeName);
    }

    @Test
    public void noNextLinkMeansLastPage() {
        String json = "{\"data\":{\"episodes\":[]},\"links\":{\"next\":null}}";
        assertFalse(V4Parser.parseEpisodes(json).hasNext());
    }
}
