package org.tvrenamer.controller.tvdb;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.tvrenamer.controller.tvdb.TvdbV4Transport.TvdbHttpResponse;
import org.tvrenamer.model.TVRenamerIOException;

public class TvdbV4ClientTest {

    /** Scriptable transport: returns queued responses, records requests. */
    static final class FakeTransport implements TvdbV4Transport {
        final List<String> calls = new ArrayList<>();
        final List<TvdbHttpResponse> getQueue = new ArrayList<>();
        int logins = 0;
        /** When set, returned as the login response body instead of the default success shape. */
        String loginBodyOverride;

        @Override
        public TvdbHttpResponse post(String url, String body, Map<String, String> h) {
            calls.add("POST " + url);
            if (url.endsWith("/login")) {
                logins++;
                if (loginBodyOverride != null) {
                    return new TvdbHttpResponse(200, loginBodyOverride);
                }
                return new TvdbHttpResponse(200, "{\"status\":\"success\",\"data\":{\"token\":\"tok" + logins + "\"}}");
            }
            return new TvdbHttpResponse(404, "");
        }

        @Override
        public TvdbHttpResponse get(String url, Map<String, String> h) {
            calls.add("GET " + url + " auth=" + h.get("Authorization"));
            return getQueue.remove(0);
        }
    }

    @Test
    public void loginOnceThenReuseToken() throws Exception {
        FakeTransport t = new FakeTransport();
        t.getQueue.add(new TvdbHttpResponse(200, "{\"ok\":1}"));
        t.getQueue.add(new TvdbHttpResponse(200, "{\"ok\":2}"));
        TvdbV4Client c = new TvdbV4Client(t, () -> "key");

        c.searchSeriesJson("solar drift");
        c.searchSeriesJson("westmark");

        assertEquals(1, t.logins, "should log in once and reuse token");
        assertTrue(t.calls.stream().anyMatch(s -> s.contains("auth=Bearer tok1")));
    }

    @Test
    public void reloginAndRetryOn401() throws Exception {
        FakeTransport t = new FakeTransport();
        t.getQueue.add(new TvdbHttpResponse(401, ""));           // first GET: expired
        t.getQueue.add(new TvdbHttpResponse(200, "{\"ok\":1}")); // retry after re-login
        TvdbV4Client c = new TvdbV4Client(t, () -> "key");

        String body = c.searchSeriesJson("solar drift");
        assertEquals("{\"ok\":1}", body);
        assertEquals(2, t.logins, "expired token forces a second login");
    }

    @Test
    public void secondConsecutive401Throws() {
        FakeTransport t = new FakeTransport();
        t.getQueue.add(new TvdbHttpResponse(401, ""));
        t.getQueue.add(new TvdbHttpResponse(401, ""));
        TvdbV4Client c = new TvdbV4Client(t, () -> "key");
        assertThrows(TVRenamerIOException.class, () -> c.searchSeriesJson("x"));
    }

    @Test
    public void blankKeyFailsFastWithoutNetwork() {
        FakeTransport t = new FakeTransport();
        TvdbV4Client c = new TvdbV4Client(t, () -> "  ");
        assertThrows(TVRenamerIOException.class, () -> c.searchSeriesJson("x"));
        assertTrue(t.calls.isEmpty(), "must not touch the network with a blank key");
    }

    @Test
    public void emptyDataObjectInLoginBodyThrowsInsteadOfNpe() {
        FakeTransport t = new FakeTransport();
        t.loginBodyOverride = "{\"data\":{}}";
        TvdbV4Client c = new TvdbV4Client(t, () -> "key");
        assertThrows(TVRenamerIOException.class, () -> c.searchSeriesJson("x"));
    }

    @Test
    public void nullDataInLoginBodyThrowsInsteadOfClassCastException() {
        FakeTransport t = new FakeTransport();
        t.loginBodyOverride = "{\"data\":null}";
        TvdbV4Client c = new TvdbV4Client(t, () -> "key");
        assertThrows(TVRenamerIOException.class, () -> c.searchSeriesJson("x"));
    }
}
