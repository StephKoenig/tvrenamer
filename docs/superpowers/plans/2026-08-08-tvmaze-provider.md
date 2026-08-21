# TVMaze Provider Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a keyless TVMaze `EpisodeDataProvider`, make it the default, and remove the dead TheTVDB v1 provider.

**Architecture:** TVMaze (`https://api.tvmaze.com`, no auth) is a third provider behind the existing `EpisodeDataProvider` interface, built to mirror the v4 provider (client + parser + provider), reusing the existing HTTP transport seam. The `episodeDataProvider` preference replaces `TVDB_V1` with `TVMAZE` (new default); v1 code is deleted. Because TVMaze and TheTVDB use different id namespaces, the shared `Series`/`ShowName` caches are cleared on a provider switch.

**Tech Stack:** Java 17 / JDK 21 toolchain, SWT, Gradle, JUnit Jupiter, Gson, `java.net.http`.

**Spec:** `docs/TVMaze Provider Spec.md` (verified live TVMaze shapes 2026-08-08).

## Global Constraints

- Java 17 bytecode target; no new dependencies (Gson already present).
- **No real show/service/scene names** in code, tests, or docs (naming "TVMaze"/"TheTVDB" — the APIs — is allowed). Test JSON uses fictional names (e.g. "Solar Drift", "Westmark Academy", "The Quiet Ones").
- TVMaze is **keyless**; the v4 provider stays exactly as-is (key, Validate, Title language).
- **TVMaze is the new default** provider.
- Never crash on a provider switch: clear the shared caches before re-matching (TVMaze/TheTVDB id namespaces differ; `Series.createSeries` throws on same-id/different-name).
- Malformed JSON must degrade gracefully (empty results), not throw.
- 4-space indent; no LF/CRLF churn. `./gradlew build` after each task; `shadowJar createExe` when UI/packaging changes.
- Preferences use hand-rolled XML persistence (already wired for `episodeDataProvider`).

---

## File structure

**New** (package `org.tvrenamer.controller.tvmaze`)
- `TvMazeClient.java` — keyless GET for search + episodes; 429 retry-once.
- `TvMazeParser.java` — pure JSON (top-level arrays) → model.
- `TvMazeProvider.java` — implements `EpisodeDataProvider`.
- Tests under `src/test/java/org/tvrenamer/controller/tvmaze/` and `.../controller/`, `.../model/`.

**Modified**
- `model/EpisodeDataProviderType.java` — remove `TVDB_V1`, add `TVMAZE`.
- `model/UserPreferences.java` — default `TVMAZE`.
- `controller/TvdbProviders.java` — `TVMAZE`→`TvMazeProvider`; remove v1 branch.
- `model/Series.java`, `model/ShowName.java` — cache-clear methods.
- `view/ResultsTable.java` — clear caches on provider switch.
- `model/util/Constants.java` — remove now-unused v1 constants.
- `src/main/resources/help/*.html`, `README.md`, `docs/Completed.md`, `docs/TODO.md`.

**Deleted**
- `controller/TheTVDBLegacyProvider.java`, `controller/TheTVDBProvider.java` (v1 XML), and v1-only tests.

---

## Task 1: TvMazeClient (keyless GET + 429 retry)

**Files:**
- Create: `src/main/java/org/tvrenamer/controller/tvmaze/TvMazeClient.java`
- Test: `src/test/java/org/tvrenamer/controller/tvmaze/TvMazeClientTest.java`

**Interfaces:**
- Consumes: `org.tvrenamer.controller.tvdb.TvdbV4Transport` (+ nested `TvdbHttpResponse`) and `JdkHttpTransport` (the existing generic HTTP seam — reused, not duplicated); `org.tvrenamer.model.TVRenamerIOException`.
- Produces: `new TvMazeClient()` and test seam `new TvMazeClient(TvdbV4Transport transport, long retryDelayMs)`; `String searchShowsJson(String query)`; `String episodesJson(int showId)`; base `https://api.tvmaze.com`.

- [ ] **Step 1: Write the failing test**

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'org.tvrenamer.controller.tvmaze.TvMazeClientTest'`
Expected: FAIL — `TvMazeClient` does not exist.

- [ ] **Step 3: Implement the client**

```java
package org.tvrenamer.controller.tvmaze;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.tvrenamer.controller.tvdb.JdkHttpTransport;
import org.tvrenamer.controller.tvdb.TvdbV4Transport;
import org.tvrenamer.controller.tvdb.TvdbV4Transport.TvdbHttpResponse;
import org.tvrenamer.model.TVRenamerIOException;

/** Keyless HTTP client for the TVMaze API (api.tvmaze.com). */
public class TvMazeClient {

    static final String BASE_URL = "https://api.tvmaze.com";
    private static final long DEFAULT_RETRY_DELAY_MS = 1500L;

    private final TvdbV4Transport transport;
    private final long retryDelayMs;

    public TvMazeClient() {
        this(new JdkHttpTransport(), DEFAULT_RETRY_DELAY_MS);
    }

    // Test seam: inject transport and a (possibly zero) retry delay.
    public TvMazeClient(TvdbV4Transport transport, long retryDelayMs) {
        this.transport = transport;
        this.retryDelayMs = retryDelayMs;
    }

    public String searchShowsJson(String query) throws TVRenamerIOException {
        String enc = URLEncoder.encode(query == null ? "" : query, StandardCharsets.UTF_8);
        return get("/search/shows?q=" + enc);
    }

    public String episodesJson(int showId) throws TVRenamerIOException {
        return get("/shows/" + showId + "/episodes");
    }

    private String get(String path) throws TVRenamerIOException {
        Map<String, String> headers = Map.of("Accept", "application/json");
        try {
            TvdbHttpResponse resp = transport.get(BASE_URL + path, headers);
            if (resp.status() == 429) {
                // Rate limited: back off briefly and retry once.
                if (retryDelayMs > 0) {
                    try {
                        Thread.sleep(retryDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new TVRenamerIOException("TVMaze retry interrupted", ie);
                    }
                }
                resp = transport.get(BASE_URL + path, headers);
            }
            if (resp.status() != 200) {
                throw new TVRenamerIOException(
                    "TVMaze request failed (HTTP " + resp.status() + "): " + path);
            }
            return resp.body();
        } catch (IOException e) {
            throw new TVRenamerIOException("TVMaze request error: " + e.getMessage(), e);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests 'org.tvrenamer.controller.tvmaze.TvMazeClientTest'`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/tvrenamer/controller/tvmaze/TvMazeClient.java src/test/java/org/tvrenamer/controller/tvmaze/TvMazeClientTest.java
git commit -m "feat: add keyless TvMazeClient (search + episodes, 429 retry)"
```

---

## Task 2: TvMazeParser (search + episodes)

**Files:**
- Create: `src/main/java/org/tvrenamer/controller/tvmaze/TvMazeParser.java`
- Test: `src/test/java/org/tvrenamer/controller/tvmaze/TvMazeParserTest.java`

**Interfaces:**
- Consumes: Gson; `org.tvrenamer.model.EpisodeInfo` (+ `EpisodeInfo.Builder` with `episodeId/seasonNumber/episodeNumber/episodeName/firstAired`).
- Produces:
  - `record TvMazeResult(String id, String name, Integer year, java.util.List<String> aliases)`
  - `static java.util.List<TvMazeResult> parseSearchShows(String json)`
  - `static java.util.List<EpisodeInfo> parseEpisodes(String json)`

- [ ] **Step 1: Write the failing test (fictional data; TVMaze responses are top-level JSON arrays)**

```java
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
```

> NOTE: `EpisodeInfo` fields are `public final` (no getters) — confirmed by the v4 work; assert `eps.get(0).seasonNumber` etc. directly. Builder setter names are `episodeId/seasonNumber/episodeNumber/episodeName/firstAired`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'org.tvrenamer.controller.tvmaze.TvMazeParserTest'`
Expected: FAIL — `TvMazeParser` does not exist.

- [ ] **Step 3: Implement the parser**

```java
package org.tvrenamer.controller.tvmaze;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.tvrenamer.model.EpisodeInfo;

/** Pure JSON -> model parsing for TVMaze responses (top-level arrays). No I/O. */
public final class TvMazeParser {

    private static final Gson GSON = new Gson();

    private TvMazeParser() {}

    public record TvMazeResult(String id, String name, Integer year, List<String> aliases) {}

    public static List<TvMazeResult> parseSearchShows(String json) {
        List<TvMazeResult> out = new ArrayList<>();
        JsonArray arr = asArray(json);
        if (arr == null) {
            return out;
        }
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject wrapper = el.getAsJsonObject();
            if (!wrapper.has("show") || !wrapper.get("show").isJsonObject()) {
                continue;
            }
            JsonObject show = wrapper.getAsJsonObject("show");
            String id = intAsString(show, "id");
            String name = str(show, "name");
            if (id == null || name == null) {
                continue;
            }
            Integer year = null;
            String premiered = str(show, "premiered");
            if (premiered != null && premiered.length() >= 4) {
                try {
                    year = Integer.parseInt(premiered.substring(0, 4));
                } catch (NumberFormatException ignored) {
                    year = null;
                }
            }
            out.add(new TvMazeResult(id, name, year, Collections.emptyList()));
        }
        return out;
    }

    public static List<EpisodeInfo> parseEpisodes(String json) {
        List<EpisodeInfo> out = new ArrayList<>();
        JsonArray arr = asArray(json);
        if (arr == null) {
            return out;
        }
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject e = el.getAsJsonObject();
            String season = intAsString(e, "season");
            String number = intAsString(e, "number");
            if (season == null || number == null) {
                continue;
            }
            out.add(new EpisodeInfo.Builder()
                .episodeId(intAsString(e, "id"))
                .seasonNumber(season)
                .episodeNumber(number)
                .episodeName(str(e, "name"))
                .firstAired(str(e, "airdate"))
                .build());
        }
        return out;
    }

    private static JsonArray asArray(String json) {
        try {
            JsonElement root = GSON.fromJson(json, JsonElement.class);
            return (root != null && root.isJsonArray()) ? root.getAsJsonArray() : null;
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    /** Trimmed string for key, or null if absent/JSON-null/blank/non-primitive. */
    private static String str(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return null;
        }
        JsonElement el = o.get(key);
        if (!el.isJsonPrimitive()) {
            return null;
        }
        String s = el.getAsString();
        return (s == null || s.isEmpty()) ? null : s;
    }

    /** Numeric field rendered as a String (TVMaze ids/season/number are integers), or null. */
    private static String intAsString(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return null;
        }
        JsonElement el = o.get(key);
        if (!el.isJsonPrimitive()) {
            return null;
        }
        try {
            return String.valueOf(el.getAsInt());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests 'org.tvrenamer.controller.tvmaze.TvMazeParserTest'` then `./gradlew build`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/tvrenamer/controller/tvmaze/TvMazeParser.java src/test/java/org/tvrenamer/controller/tvmaze/TvMazeParserTest.java
git commit -m "feat: add TvMazeParser (search shows + episodes)"
```

---

## Task 3: TvMazeProvider (implements EpisodeDataProvider)

**Files:**
- Create: `src/main/java/org/tvrenamer/controller/TvMazeProvider.java`
- Test: `src/test/java/org/tvrenamer/controller/TvMazeProviderTest.java`

**Interfaces:**
- Consumes: `EpisodeDataProvider`; `TvMazeClient`, `TvMazeParser` (Tasks 1-2); `ShowName.addShowOption(String,String,Integer,List<String>)`, `ShowName.clearShowOptions()`, `ShowName.getQueryString()`; `Series.getId()`, `setPreferDvd(boolean)`, `addEpisodeInfos(EpisodeInfo[])`, `listingsSucceeded()`.
- Produces: `TvMazeProvider` (no-arg ctor builds a real `TvMazeClient`; test-seam ctor takes a `TvMazeClient`).

- [ ] **Step 1: Write the failing test**

```java
package org.tvrenamer.controller;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.tvrenamer.controller.tvdb.TvdbV4Transport;
import org.tvrenamer.controller.tvdb.TvdbV4Transport.TvdbHttpResponse;
import org.tvrenamer.controller.tvmaze.TvMazeClient;
import org.tvrenamer.model.Series;
import org.tvrenamer.model.ShowName;
import org.tvrenamer.model.ShowOption;

public class TvMazeProviderTest {

    private static TvMazeClient clientReturning(java.util.function.Function<String,String> byUrl) {
        TvdbV4Transport t = new TvdbV4Transport() {
            public TvdbHttpResponse post(String u, String b, Map<String,String> h) {
                return new TvdbHttpResponse(405, "");
            }
            public TvdbHttpResponse get(String u, Map<String,String> h) {
                return new TvdbHttpResponse(200, byUrl.apply(u));
            }
        };
        return new TvMazeClient(t, 0);
    }

    @Test
    public void getShowOptionsPopulatesShowName() throws Exception {
        TvMazeClient c = clientReturning(u ->
            "[{\"score\":1.0,\"show\":{\"id\":38052,\"name\":\"Solar Drift\",\"premiered\":\"2019-01-01\"}}]");
        TvMazeProvider p = new TvMazeProvider(c);
        ShowName sn = ShowName.mapShowName("solar drift");
        p.getShowOptions(sn);
        List<ShowOption> opts = sn.getShowOptions();
        assertEquals(1, opts.size());
        assertEquals("38052", opts.get(0).getIdString());
    }

    @Test
    public void getSeriesListingPopulatesEpisodes() throws Exception {
        TvMazeClient c = clientReturning(u ->
            "[{\"id\":1,\"season\":1,\"number\":1,\"name\":\"Pilot\",\"airdate\":\"2019-01-01\"}]");
        TvMazeProvider p = new TvMazeProvider(c);
        Series s = Series.createSeries(990101, "Solar Drift");
        p.getSeriesListing(s);
        assertFalse(s.noEpisodes(), "episodes should be populated");
    }
}
```

> NOTE: confirm `ShowName.mapShowName(String)`, `ShowName.getShowOptions()`, `ShowOption.getIdString()`, and `Series.noEpisodes()` exist (all used by existing tests/providers). Adjust if a signature differs.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'org.tvrenamer.controller.TvMazeProviderTest'`
Expected: FAIL — `TvMazeProvider` does not exist.

- [ ] **Step 3: Implement the provider**

```java
package org.tvrenamer.controller;

import org.tvrenamer.controller.tvmaze.TvMazeClient;
import org.tvrenamer.controller.tvmaze.TvMazeParser;
import org.tvrenamer.controller.tvmaze.TvMazeParser.TvMazeResult;
import org.tvrenamer.model.EpisodeInfo;
import org.tvrenamer.model.Series;
import org.tvrenamer.model.ShowName;
import org.tvrenamer.model.TVRenamerIOException;

import java.util.List;

/** EpisodeDataProvider backed by the keyless TVMaze API. */
public class TvMazeProvider implements EpisodeDataProvider {

    private final TvMazeClient client;

    public TvMazeProvider() {
        this(new TvMazeClient());
    }

    // Test seam.
    public TvMazeProvider(TvMazeClient client) {
        this.client = client;
    }

    @Override
    public void getShowOptions(ShowName showName) throws TVRenamerIOException {
        showName.clearShowOptions();
        String json = client.searchShowsJson(showName.getQueryString());
        for (TvMazeResult r : TvMazeParser.parseSearchShows(json)) {
            showName.addShowOption(r.id(), r.name(), r.year(), r.aliases());
        }
    }

    @Override
    public void getSeriesListing(Series series) throws TVRenamerIOException {
        List<EpisodeInfo> episodes =
            TvMazeParser.parseEpisodes(client.episodesJson(series.getId()));
        // TVMaze has a single episode ordering (no DVD variant).
        series.setPreferDvd(false);
        series.addEpisodeInfos(episodes.toArray(new EpisodeInfo[0]));
        series.listingsSucceeded();
    }
}
```

> NOTE: verify `EpisodeDataProvider`'s method signatures (throws clause). `getShowOptions` in the interface declares `throws TVRenamerIOException, DiscontinuedApiException`; implementing with only `throws TVRenamerIOException` is legal (narrower). Match the interface exactly if the compiler complains.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests 'org.tvrenamer.controller.TvMazeProviderTest'` then `./gradlew build`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/tvrenamer/controller/TvMazeProvider.java src/test/java/org/tvrenamer/controller/TvMazeProviderTest.java
git commit -m "feat: add TvMazeProvider implementing EpisodeDataProvider"
```

---

## Task 4: Cache-clear methods on Series and ShowName

**Files:**
- Modify: `model/Series.java`, `model/ShowName.java`
- Test: `src/test/java/org/tvrenamer/model/CacheClearTest.java`

**Interfaces:**
- Produces: `static void Series.clearKnownSeries()`; `static void ShowName.clearAllQueryCache()`.

- [ ] **Step 1: Write the failing test**

```java
package org.tvrenamer.model;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class CacheClearTest {

    @Test
    public void clearKnownSeriesAllowsSameIdDifferentName() {
        int id = 970001;
        Series.createSeries(id, "Solar Drift");
        // Same id + different name would normally throw (id-collision guard).
        assertThrows(IllegalArgumentException.class,
            () -> Series.createSeries(id, "Westmark Academy"));
        Series.clearKnownSeries();
        // After clearing, the id is free to be re-created with a different name.
        Series reused = Series.createSeries(id, "Westmark Academy");
        assertEquals("Westmark Academy", reused.getName());
    }

    @Test
    public void clearAllQueryCacheDoesNotThrow() {
        ShowName.mapShowName("solar drift");
        ShowName.clearAllQueryCache();
        // A fresh mapping after clear returns a usable ShowName.
        assertNotNull(ShowName.mapShowName("solar drift"));
    }
}
```

> NOTE: confirm `Series.createSeries` throws `IllegalArgumentException` on same-id/different-name (existing `SeriesTest` relies on it) and that `getName()` returns the created name. Confirm the ShowName query-cache field name (e.g. `QUERY_STRINGS`) before writing `clearAllQueryCache`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'org.tvrenamer.model.CacheClearTest'`
Expected: FAIL — clear methods don't exist.

- [ ] **Step 3: Implement the clear methods**

In `Series.java` (near the `KNOWN_SERIES` map):
```java
    /**
     * Clear the id -> Series cache. Called on a provider switch so ids from a
     * different provider's namespace cannot collide with (or be wrongly reused
     * from) the previous provider.
     */
    public static void clearKnownSeries() {
        KNOWN_SERIES.clear();
    }
```
In `ShowName.java` (near the query-string cache map — confirm the field name, shown here as `QUERY_STRINGS`):
```java
    /** Clear the query-string -> ShowName cache (called on a provider switch). */
    public static void clearAllQueryCache() {
        QUERY_STRINGS.clear();
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests 'org.tvrenamer.model.CacheClearTest'` then `./gradlew build`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/tvrenamer/model/Series.java src/main/java/org/tvrenamer/model/ShowName.java src/test/java/org/tvrenamer/model/CacheClearTest.java
git commit -m "feat: add cache-clear methods for provider switches"
```

---

## Task 5: Swap provider — TVMAZE default, remove v1, wire cache-clear

**Files:**
- Modify: `model/EpisodeDataProviderType.java`, `model/UserPreferences.java`, `controller/TvdbProviders.java`, `view/ResultsTable.java`, `model/util/Constants.java`
- Delete: `controller/TheTVDBLegacyProvider.java`, `controller/TheTVDBProvider.java`, v1 tests
- Test: update `model/ProviderPreferencesTest.java`, `controller/TvdbProvidersTest.java`

**Interfaces:**
- Consumes: `TvMazeProvider` (Task 3); `Series.clearKnownSeries()` + `ShowName.clearAllQueryCache()` (Task 4).
- Produces: `EpisodeDataProviderType.TVMAZE` (no `TVDB_V1`); default provider `TVMAZE`; `TvdbProviders.current()` returns `TvMazeProvider` for `TVMAZE`, `TheTVDBv4Provider` for `TVDB_V4`.

- [ ] **Step 1: Update the enum**

In `EpisodeDataProviderType.java`, remove the `TVDB_V1(...)` constant and add:
```java
    TVMAZE("TVMaze"),
```
so the constants are `TVMAZE` and `TVDB_V4`. Leave `toString()`/`fromString(...)` as-is (case-insensitive). `fromString("TVDB_V1")` now returns null (handled by callers' default fallback).

- [ ] **Step 2: Default to TVMAZE in UserPreferences**

In the private constructor, change `episodeDataProvider = EpisodeDataProviderType.TVDB_V1;` to:
```java
        episodeDataProvider = EpisodeDataProviderType.TVMAZE;
```
Confirm the `fromParsedXml` fallback and `setEpisodeDataProvider` null-guard also resolve to `TVMAZE` (they reference the same default constant or `TVMAZE` explicitly — update any literal `TVDB_V1` fallback to `TVMAZE`).

- [ ] **Step 3: Rewire the selector**

Replace `TvdbProviders.java` body:
```java
    private static final EpisodeDataProvider TVMAZE = new TvMazeProvider();
    private static final EpisodeDataProvider V4 = new TheTVDBv4Provider();

    private TvdbProviders() {}

    public static EpisodeDataProvider current() {
        EpisodeDataProviderType type =
            UserPreferences.getInstance().getEpisodeDataProvider();
        return (type == EpisodeDataProviderType.TVDB_V4) ? V4 : TVMAZE;
    }
```
Remove the `TheTVDBLegacyProvider` field and import.

- [ ] **Step 4: Clear caches on provider switch (ResultsTable)**

In `updateUserPreferences`, change the `EPISODE_DATA_PROVIDER` case to clear the shared caches before re-matching:
```java
            case EPISODE_DATA_PROVIDER:
                // Provider ids live in different namespaces; drop cross-provider
                // caches so a switch can't reuse or collide with the old provider.
                Series.clearKnownSeries();
                ShowName.clearAllQueryCache();
                rematchRows(FileEpisode::isShowUnfound);
                break;
```
Add imports for `Series`/`ShowName` if not already present.

- [ ] **Step 5: Delete v1 code + update references**

- `git rm src/main/java/org/tvrenamer/controller/TheTVDBLegacyProvider.java src/main/java/org/tvrenamer/controller/TheTVDBProvider.java`
- Delete v1-only tests: `git rm src/test/java/org/tvrenamer/controller/TheTVDBProviderTest.java` (and any other test that references `TheTVDBProvider`/`TheTVDBLegacyProvider` for v1 — grep first: `grep -rn "TheTVDBProvider\|TheTVDBLegacyProvider" src/`; if an integration test does live v1 lookups, remove it).
- In `Constants.java`, remove `TVDB_API_KEY` and `DEFAULT_LANGUAGE` **only if** grep shows no remaining references (`grep -rn "TVDB_API_KEY\|DEFAULT_LANGUAGE" src/`). If either is still referenced by non-v1 code, leave it.
- Grep for any remaining `EpisodeDataProviderType.TVDB_V1` and update.

- [ ] **Step 6: Update the two affected tests**

- `ProviderPreferencesTest`: the default is now `TVMAZE`, not `TVDB_V1`. Update the default-assertion test (rename/retarget `defaultsToV1AndEmptyKey` → assert `getEpisodeDataProvider() == TVMAZE`; the empty-key assertion stays). Update any `@AfterEach` reset that set `TVDB_V1` to `TVMAZE`, and the persistence-round-trip provider value if it used `TVDB_V1`.
- `TvdbProvidersTest`: `selectorFollowsPreference` — set `TVMAZE` → assert `instanceof TvMazeProvider`; set `TVDB_V4` → assert `instanceof TheTVDBv4Provider`; reset to `TVMAZE` in the finally.

- [ ] **Step 7: Run tests + full build**

Run: `./gradlew test --tests 'org.tvrenamer.model.*' --tests 'org.tvrenamer.controller.*'`
Then: `./gradlew build`
Expected: PASS; no compile references to removed v1 classes/constants.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: make TVMaze the default provider; remove deprecated v1"
```

---

## Task 6: UI check + documentation

**Files:**
- Verify: `view/PreferencesDialog.java` (dropdown auto-populates from `EpisodeDataProviderType.values()` — no code change expected; confirm)
- Modify: `src/main/resources/help/preferences.html`, `src/main/resources/help/troubleshooting.html`, `README.md`, `docs/Completed.md`, `docs/TODO.md`

- [ ] **Step 1: Confirm the provider dropdown**

Read `PreferencesDialog.populateGeneralTab` / `initializeGeneralControls` / `savePreferences`: the provider combo is populated from `EpisodeDataProviderType.values()` and round-trips via `toString()`/`fromString()`, so removing `TVDB_V1`/adding `TVMAZE` needs no dialog code change. `updateProviderControlsEnabled()` gates the key/Validate/Title-language controls on `TVDB_V4`, so they stay disabled for `TVMAZE`. If any of these assumptions is false, make the minimal change and note it; otherwise no edit.

- [ ] **Step 2: Help — preferences.html**

Update the TV-data-provider section: providers are now **TVMaze** (default, no API key required) and **TheTVDB (v4)** (requires a key). Note TVMaze is keyless and the simplest choice; the API-key, Validate, and Title-language settings apply only to v4. Remove any mention of the v1 provider.

- [ ] **Step 3: Help — troubleshooting.html**

Update the "No info" guidance: switch the **TV data provider** between TVMaze and TheTVDB v4 (drop the v1 reference). TVMaze needs no key.

- [ ] **Step 4: README**

Update the data-providers section: TVMaze (keyless default) and TheTVDB v4 (key required, Title-language support). Remove v1.

- [ ] **Step 5: Completed.md + TODO.md**

Add the next `docs/Completed.md` entry (current highest is #59; use #60) with Title/Why/Where/What/Notes summarizing: keyless TvMazeClient/TvMazeParser/TvMazeProvider; TVMAZE default; v1 removed; cache-clear on provider switch. In `docs/TODO.md`, remove/adjust any items that referenced the v1 provider as available; note TVMaze rate-limit (429 retry-once) as a known limitation for very large batches.

- [ ] **Step 6: Build + commit**

```bash
./gradlew build
git add -A
git commit -m "docs: TVMaze as default provider; v1 removed"
```

---

## Final verification

- [ ] `./gradlew clean build shadowJar createExe` green.
- [ ] Manual (Windows): fresh prefs (or old `TVDB_V1` value) → provider defaults to **TVMaze**, no key; add files → resolve via TVMaze. Switch TVMaze ↔ v4 → no crash, rows re-fetch. v4-only controls disabled under TVMaze.
- [ ] `-Dtvrenamer.debug=true`: TVMaze `/search/shows` + `/shows/{id}/episodes` calls, no auth header.

---

## Self-review notes (author)

- **Spec coverage:** client+429 (Task 1); parser (Task 2); provider (Task 3); cache-clear (Task 4); enum/default/selector/v1-removal/cache-wiring (Task 5); UI-confirm + docs (Task 6). All spec sections covered.
- **Assumptions flagged inline:** `ShowName` query-cache field name (Task 4), `Series.noEpisodes()`/`ShowName.mapShowName` signatures (Task 3), exact v1 references to delete (Task 5 grep), and whether `DEFAULT_LANGUAGE`/`TVDB_API_KEY` are v1-only (Task 5 grep).
- **Build stays green per task:** the TVMaze client/parser/provider (Tasks 1-3) are self-contained new files; the enum swap + v1 deletion happen together in Task 5 once `TvMazeProvider` exists, so no task leaves a broken reference.
- **Type consistency:** `TvMazeClient(TvdbV4Transport,long)`, `searchShowsJson`/`episodesJson`, `TvMazeParser.parseSearchShows`/`parseEpisodes`/`TvMazeResult`, `TvMazeProvider`, `Series.clearKnownSeries`/`ShowName.clearAllQueryCache`, `EpisodeDataProviderType.TVMAZE` used identically across tasks.
