package org.tvrenamer.controller.tvmaze;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.tvrenamer.controller.tvdb.TvdbV4Transport;
import org.tvrenamer.controller.tvdb.TvdbV4Transport.TvdbHttpResponse;
import org.tvrenamer.model.TVRenamerIOException;

public class TvMazeClientTest {

    static final class FakeTransport implements TvdbV4Transport {
        final List<String> calls = new ArrayList<>();
        final List<TvdbHttpResponse> getQueue = new ArrayList<>();
        public TvdbHttpResponse post(String u, String b, Map<String,String> h) {
            return new TvdbHttpResponse(405, "");
        }
        public TvdbHttpResponse get(String u, Map<String,String> h) {
            calls.add("GET " + u);
            return getQueue.remove(0);
        }
    }

    @Test
    public void searchUrlEncodesQuery() throws Exception {
        FakeTransport t = new FakeTransport();
        t.getQueue.add(new TvdbHttpResponse(200, "[]"));
        new TvMazeClient(t, 0).searchShowsJson("solar drift");
        assertTrue(t.calls.stream().anyMatch(
            s -> s.contains("https://api.tvmaze.com/search/shows?q=solar%20drift")
              || s.contains("/search/shows?q=solar+drift")),
            "calls=" + t.calls);
    }

    @Test
    public void episodesUrlIsCorrect() throws Exception {
        FakeTransport t = new FakeTransport();
        t.getQueue.add(new TvdbHttpResponse(200, "[]"));
        new TvMazeClient(t, 0).episodesJson(38052);
        assertTrue(t.calls.stream().anyMatch(s -> s.contains("/shows/38052/episodes")),
            "calls=" + t.calls);
    }

    @Test
    public void retriesOnceOn429ThenSucceeds() throws Exception {
        FakeTransport t = new FakeTransport();
        t.getQueue.add(new TvdbHttpResponse(429, ""));
        t.getQueue.add(new TvdbHttpResponse(200, "[]"));
        assertEquals("[]", new TvMazeClient(t, 0).searchShowsJson("x"));
        assertEquals(2, t.calls.size(), "should retry once");
    }

    @Test
    public void secondConsecutive429Throws() {
        FakeTransport t = new FakeTransport();
        t.getQueue.add(new TvdbHttpResponse(429, ""));
        t.getQueue.add(new TvdbHttpResponse(429, ""));
        assertThrows(TVRenamerIOException.class, () -> new TvMazeClient(t, 0).searchShowsJson("x"));
    }

    @Test
    public void non200Throws() {
        FakeTransport t = new FakeTransport();
        t.getQueue.add(new TvdbHttpResponse(500, ""));
        assertThrows(TVRenamerIOException.class, () -> new TvMazeClient(t, 0).episodesJson(1));
    }
}
