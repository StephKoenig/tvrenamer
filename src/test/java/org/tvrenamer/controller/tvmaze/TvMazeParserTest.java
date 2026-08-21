package org.tvrenamer.controller.tvmaze;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.tvrenamer.controller.tvmaze.TvMazeParser.TvMazeResult;
import org.tvrenamer.model.EpisodeInfo;

public class TvMazeParserTest {

    @Test
    public void parsesSearchShows() {
        String json = "["
            + "{\"score\":0.9,\"show\":{\"id\":38052,\"name\":\"Solar Drift\","
            + "\"premiered\":\"2019-05-05\",\"language\":\"English\"}},"
            + "{\"score\":0.5,\"show\":{\"id\":56955,\"name\":\"Westmark Academy\","
            + "\"premiered\":null,\"language\":\"English\"}}]";
        List<TvMazeResult> r = TvMazeParser.parseSearchShows(json);
        assertEquals(2, r.size());
        assertEquals("38052", r.get(0).id());
        assertEquals("Solar Drift", r.get(0).name());
        assertEquals(Integer.valueOf(2019), r.get(0).year());
        assertTrue(r.get(0).aliases().isEmpty());
        assertNull(r.get(1).year(), "null premiered -> null year");
    }

    @Test
    public void searchSkipsMalformedAndEmptyOnBadJson() {
        // element missing show -> skipped; still returns the valid one
        String json = "[{\"score\":0.1},{\"show\":{\"id\":7,\"name\":\"The Quiet Ones\"}}]";
        List<TvMazeResult> r = TvMazeParser.parseSearchShows(json);
        assertEquals(1, r.size());
        assertEquals("7", r.get(0).id());
        assertTrue(TvMazeParser.parseSearchShows("not json[").isEmpty());
    }

    @Test
    public void parsesEpisodes() {
        String json = "["
            + "{\"id\":2500484,\"season\":1,\"number\":1,\"name\":\"Pilot\",\"airdate\":\"2019-05-05\"},"
            + "{\"id\":2500487,\"season\":1,\"number\":2,\"name\":null,\"airdate\":\"2019-05-12\"}]";
        List<EpisodeInfo> eps = TvMazeParser.parseEpisodes(json);
        assertEquals(2, eps.size());
        assertEquals("1", eps.get(0).seasonNumber);
        assertEquals("1", eps.get(0).episodeNumber);
        assertEquals("Pilot", eps.get(0).episodeName);
    }

    @Test
    public void episodesSkipMissingSeasonOrNumberAndEmptyOnBadJson() {
        String json = "[{\"id\":1,\"name\":\"No placement\"},"
            + "{\"id\":2,\"season\":2,\"number\":5,\"name\":\"Ok\",\"airdate\":\"2020-01-01\"}]";
        List<EpisodeInfo> eps = TvMazeParser.parseEpisodes(json);
        assertEquals(1, eps.size());
        assertEquals("2", eps.get(0).seasonNumber);
        assertTrue(TvMazeParser.parseEpisodes("not json[").isEmpty());
    }
}
