# TheTVDB v4 Provider Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add TheTVDB **v4** REST/JSON API as an operator-selectable second episode-data provider, so lookups work while the v1 name-search index is returning empty; v1 stays the keyless default.

**Architecture:** A new `EpisodeDataProvider` interface has two implementations — `TheTVDBLegacyProvider` (a thin wrapper over the existing v1 `TheTVDBProvider`, unchanged) and `TheTVDBv4Provider` (new). A `TvdbProviders.current()` selector reads a new preference and is swapped into the 4 existing provider call sites. Both implementations populate the *same* models (`ShowName.addShowOption`, `Series.addEpisodeInfos`), so the evaluator, disambiguation, caching, and `FileEpisode` are untouched.

**Tech Stack:** Java 17 bytecode / JDK 21 toolchain, SWT, Gradle, JUnit Jupiter, **Gson** (new), `java.net.http.HttpClient`.

**Spec:** `docs/TVDB v4 Provider Spec.md` (verified live response shapes on 2026-08-04).

## Global Constraints

- Java build toolchain JDK 21; runtime bytecode target Java 17 — do not use APIs newer than Java 17.
- Dependency versions live ONLY in `gradle/libs.versions.toml`; after adding one run `./gradlew dependencies --write-locks`.
- **No real show/service/scene names** in code, comments, tests, or docs. Test JSON uses fictional names (e.g. "Solar Drift", "Westmark Academy", "The Quiet Ones").
- **No hardcoded v4 API key** in the repo. The key comes from `UserPreferences.getTvdbV4ApiKey()`.
- v1 `TheTVDBProvider.java` stays **byte-for-byte unchanged**.
- Match existing code style (4-space indent, existing formatting). Avoid LF/CRLF churn.
- Run `./gradlew build` after each task; `./gradlew shadowJar createExe` when UI/packaging is touched.

---

## File structure

**New files**
- `src/main/java/org/tvrenamer/model/EpisodeDataProviderType.java` — enum `TVDB_V1`/`TVDB_V4` (modeled on `ThemeMode`).
- `src/main/java/org/tvrenamer/controller/EpisodeDataProvider.java` — the interface.
- `src/main/java/org/tvrenamer/controller/TheTVDBLegacyProvider.java` — wrapper delegating to `TheTVDBProvider` statics.
- `src/main/java/org/tvrenamer/controller/TvdbProviders.java` — selector.
- `src/main/java/org/tvrenamer/controller/tvdb/TvdbV4Transport.java` — HTTP seam interface + `TvdbHttpResponse` record.
- `src/main/java/org/tvrenamer/controller/tvdb/JdkHttpTransport.java` — default transport (`java.net.http`).
- `src/main/java/org/tvrenamer/controller/tvdb/TvdbV4Client.java` — auth/token/401-retry + raw JSON fetch.
- `src/main/java/org/tvrenamer/controller/tvdb/V4Parser.java` — pure JSON→model parsing (Gson).
- `src/main/java/org/tvrenamer/controller/TheTVDBv4Provider.java` — implements `EpisodeDataProvider` using client + parser.
- Tests under `src/test/java/org/tvrenamer/controller/tvdb/` and `.../model/`.

**Modified files**
- `gradle/libs.versions.toml`, `build.gradle`, `gradle.lockfile` — add Gson.
- `src/main/java/org/tvrenamer/model/UserPreferences.java` — 2 new prefs + persistence.
- `src/main/java/org/tvrenamer/model/UserPreference.java` — 2 new enum constants.
- `src/main/java/org/tvrenamer/controller/UserPreferencesPersistence.java` — persist/read the 2 prefs.
- `src/main/java/org/tvrenamer/model/util/Constants.java` — UI label/tooltip strings.
- `src/main/java/org/tvrenamer/model/ShowStore.java:468`, `controller/ListingsLookup.java:62`, `view/PreferencesDialog.java:2291,2320` — swap to `TvdbProviders.current()`.
- `src/main/java/org/tvrenamer/view/PreferencesDialog.java` — "TV data provider" UI group + Validate button.
- `src/main/java/org/tvrenamer/view/ResultsTable.java` — `EPISODE_DATA_PROVIDER` re-match wiring.
- `src/main/resources/help/*.html`, `README*`, `docs/Completed.md`, `docs/TODO.md` — docs.

---

## Task 1: Add the Gson dependency

**Files:**
- Modify: `gradle/libs.versions.toml`, `build.gradle`, `gradle.lockfile`
- Test: `src/test/java/org/tvrenamer/controller/tvdb/GsonAvailableTest.java`

**Interfaces:**
- Produces: Gson on the compile+runtime classpath (`com.google.gson.Gson`).

- [ ] **Step 1: Write the failing test**

```java
package org.tvrenamer.controller.tvdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

public class GsonAvailableTest {
    @Test
    public void gsonParsesObject() {
        JsonObject o = new Gson().fromJson("{\"status\":\"success\"}", JsonObject.class);
        assertEquals("success", o.get("status").getAsString());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'org.tvrenamer.controller.tvdb.GsonAvailableTest'`
Expected: FAIL — compile error, `com.google.gson` does not resolve.

- [ ] **Step 3: Add Gson to the version catalog**

In `gradle/libs.versions.toml`, under `[versions]` add:
```toml
gson = "2.11.0"
```
Under `[libraries]` add:
```toml
gson = { module = "com.google.code.gson:gson", version.ref = "gson" }
```

- [ ] **Step 4: Wire it into `build.gradle`**

In the `dependencies { }` block, alongside the existing implementation deps, add:
```groovy
    implementation libs.gson
```

- [ ] **Step 5: Regenerate the lockfile**

Run: `./gradlew dependencies --write-locks`
Then confirm `gradle.lockfile` contains a `com.google.code.gson:gson:2.11.0` line.

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew test --tests 'org.tvrenamer.controller.tvdb.GsonAvailableTest'`
Expected: PASS

- [ ] **Step 7: Verify Gson is bundled in the fat JAR**

Run: `./gradlew shadowJar` then
`jar tf build/libs/tvrenamer.jar | grep -m1 com/google/gson/Gson.class`
Expected: prints `com/google/gson/Gson.class` (Gson is on the runtime classpath).

- [ ] **Step 8: Commit**

```bash
git add gradle/libs.versions.toml build.gradle gradle.lockfile src/test/java/org/tvrenamer/controller/tvdb/GsonAvailableTest.java
git commit -m "build: add Gson dependency for v4 JSON parsing"
```

---

## Task 2: Provider-type enum + two new preferences

**Files:**
- Create: `src/main/java/org/tvrenamer/model/EpisodeDataProviderType.java`
- Modify: `src/main/java/org/tvrenamer/model/UserPreferences.java`, `model/UserPreference.java`, `controller/UserPreferencesPersistence.java`
- Test: `src/test/java/org/tvrenamer/model/ProviderPreferencesTest.java`

**Interfaces:**
- Produces:
  - `EpisodeDataProviderType { TVDB_V1, TVDB_V4 }` with `toString()` (label) and `static EpisodeDataProviderType fromString(String)`.
  - `UserPreferences.getEpisodeDataProvider()` / `setEpisodeDataProvider(EpisodeDataProviderType)`
  - `UserPreferences.getTvdbV4ApiKey()` / `setTvdbV4ApiKey(String)`
  - `UserPreference.EPISODE_DATA_PROVIDER`, `UserPreference.TVDB_V4_API_KEY`

- [ ] **Step 1: Write the failing test**

```java
package org.tvrenamer.model;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tvrenamer.controller.UserPreferencesPersistence;

public class ProviderPreferencesTest {

    @Test
    public void defaultsToV1AndEmptyKey() {
        UserPreferences p = UserPreferences.getInstance();
        // Default must be the keyless v1 provider.
        assertEquals(EpisodeDataProviderType.TVDB_V1, p.getEpisodeDataProvider());
    }

    @Test
    public void enumFromStringIsCaseInsensitive() {
        assertEquals(EpisodeDataProviderType.TVDB_V4,
                     EpisodeDataProviderType.fromString("tvdb_v4"));
        assertNull(EpisodeDataProviderType.fromString("nonsense"));
    }

    @Test
    public void persistenceRoundTrip(@TempDir Path dir) {
        Path file = dir.resolve("prefs.xml");
        UserPreferences p = UserPreferences.getInstance();
        p.setEpisodeDataProvider(EpisodeDataProviderType.TVDB_V4);
        p.setTvdbV4ApiKey("test-key-1234");
        UserPreferencesPersistence.persist(p, file);

        UserPreferences read = UserPreferencesPersistence.retrieve(file);
        assertEquals(EpisodeDataProviderType.TVDB_V4, read.getEpisodeDataProvider());
        assertEquals("test-key-1234", read.getTvdbV4ApiKey());

        // reset so other tests see the default
        p.setEpisodeDataProvider(EpisodeDataProviderType.TVDB_V1);
        p.setTvdbV4ApiKey("");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'org.tvrenamer.model.ProviderPreferencesTest'`
Expected: FAIL — `EpisodeDataProviderType` and the new getters don't exist.

- [ ] **Step 3: Create the enum**

`src/main/java/org/tvrenamer/model/EpisodeDataProviderType.java` (modeled on `ThemeMode.java:14-55`):
```java
package org.tvrenamer.model;

import java.util.Locale;

/** Which TheTVDB API TVRenamer uses to look up shows and episodes. */
public enum EpisodeDataProviderType {
    TVDB_V1("TheTVDB (v1)"),
    TVDB_V4("TheTVDB (v4)");

    private final String label;

    EpisodeDataProviderType(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }

    public static EpisodeDataProviderType fromString(String value) {
        if (value == null) {
            return null;
        }
        String upper = value.trim().toUpperCase(Locale.ROOT);
        if (upper.isEmpty()) {
            return null;
        }
        for (EpisodeDataProviderType t : values()) {
            if (t.name().equals(upper)
                || t.label.toUpperCase(Locale.ROOT).equals(upper)) {
                return t;
            }
        }
        return null;
    }
}
```

- [ ] **Step 4: Add the two `UserPreference` enum constants**

In `src/main/java/org/tvrenamer/model/UserPreference.java` (enum body, near `THEME_MODE`), add:
```java
    EPISODE_DATA_PROVIDER,
    TVDB_V4_API_KEY,
```

- [ ] **Step 5: Add fields, defaults, getters, setters in `UserPreferences.java`**

Field declarations (near `private ThemeMode themeMode;`):
```java
    private EpisodeDataProviderType episodeDataProvider;
    private String tvdbV4ApiKey;
```
Defaults in the private constructor (near `themeMode = ThemeMode.LIGHT;`):
```java
        episodeDataProvider = EpisodeDataProviderType.TVDB_V1;
        tvdbV4ApiKey = "";
```
Getters + setters (mirror `getThemeMode`/`setThemeMode` at `:651-666` and `setSeasonPrefix` at `:1128-1134`):
```java
    public EpisodeDataProviderType getEpisodeDataProvider() {
        return episodeDataProvider;
    }

    public void setEpisodeDataProvider(EpisodeDataProviderType type) {
        EpisodeDataProviderType resolved =
            (type == null) ? EpisodeDataProviderType.TVDB_V1 : type;
        if (valuesAreDifferent(this.episodeDataProvider, resolved)) {
            this.episodeDataProvider = resolved;
            preferenceChanged(UserPreference.EPISODE_DATA_PROVIDER);
        }
    }

    public String getTvdbV4ApiKey() {
        return tvdbV4ApiKey;
    }

    public void setTvdbV4ApiKey(String key) {
        String resolved = (key == null) ? "" : key.trim();
        if (valuesAreDifferent(this.tvdbV4ApiKey, resolved)) {
            this.tvdbV4ApiKey = resolved;
            preferenceChanged(UserPreference.TVDB_V4_API_KEY);
        }
    }
```

- [ ] **Step 6: Deserialize in `fromParsedXml(...)`**

In `UserPreferences.fromParsedXml` (mirror the `themeMode` block at `:232-244` and the `seasonPrefix` block at `:184-187`), add:
```java
        val = scalars.get("episodeDataProvider");
        if (val != null) {
            EpisodeDataProviderType t = EpisodeDataProviderType.fromString(val);
            p.episodeDataProvider =
                (t == null) ? EpisodeDataProviderType.TVDB_V1 : t;
        }
        val = scalars.get("tvdbV4ApiKey");
        if (val != null) {
            p.tvdbV4ApiKey = val;
        }
```

- [ ] **Step 7: Register both in persistence**

In `controller/UserPreferencesPersistence.java`, add to `SCALAR_FIELDS[]` (`:29-53`):
```java
        "episodeDataProvider",
        "tvdbV4ApiKey",
```
And in `persist(...)` (near `appendElement(xml, "themeMode", prefs.getThemeMode().name());`):
```java
        appendElement(xml, "episodeDataProvider", prefs.getEpisodeDataProvider().name());
        appendElement(xml, "tvdbV4ApiKey", prefs.getTvdbV4ApiKey());
```

- [ ] **Step 8: Run test to verify it passes**

Run: `./gradlew test --tests 'org.tvrenamer.model.ProviderPreferencesTest'`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add src/main/java/org/tvrenamer/model/EpisodeDataProviderType.java src/main/java/org/tvrenamer/model/UserPreferences.java src/main/java/org/tvrenamer/model/UserPreference.java src/main/java/org/tvrenamer/controller/UserPreferencesPersistence.java src/test/java/org/tvrenamer/model/ProviderPreferencesTest.java
git commit -m "feat: add episodeDataProvider + tvdbV4ApiKey preferences"
```

---

## Task 3: v4 HTTP transport seam + JDK implementation

**Files:**
- Create: `src/main/java/org/tvrenamer/controller/tvdb/TvdbV4Transport.java`, `.../JdkHttpTransport.java`
- Test: `src/test/java/org/tvrenamer/controller/tvdb/JdkHttpTransportTest.java` (construction only; no network in CI)

**Interfaces:**
- Produces:
  - `record TvdbHttpResponse(int status, String body)`
  - `interface TvdbV4Transport { TvdbHttpResponse post(String url, String jsonBody, Map<String,String> headers) throws IOException; TvdbHttpResponse get(String url, Map<String,String> headers) throws IOException; }`
  - `class JdkHttpTransport implements TvdbV4Transport`

- [ ] **Step 1: Write the failing test**

```java
package org.tvrenamer.controller.tvdb;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

public class JdkHttpTransportTest {
    @Test
    public void constructs() {
        assertNotNull(new JdkHttpTransport());
    }

    @Test
    public void responseRecordHoldsValues() {
        TvdbHttpResponse r = new TvdbHttpResponse(200, "body");
        org.junit.jupiter.api.Assertions.assertEquals(200, r.status());
        org.junit.jupiter.api.Assertions.assertEquals("body", r.body());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'org.tvrenamer.controller.tvdb.JdkHttpTransportTest'`
Expected: FAIL — types don't exist.

- [ ] **Step 3: Create the transport interface + response record**

`TvdbV4Transport.java`:
```java
package org.tvrenamer.controller.tvdb;

import java.io.IOException;
import java.util.Map;

/** Minimal HTTP seam for the v4 client, so auth/retry/parse are testable offline. */
public interface TvdbV4Transport {

    /** HTTP result: status code + raw response body (may be empty). */
    record TvdbHttpResponse(int status, String body) {}

    TvdbHttpResponse post(String url, String jsonBody, Map<String, String> headers)
        throws IOException;

    TvdbHttpResponse get(String url, Map<String, String> headers) throws IOException;
}
```

- [ ] **Step 4: Create the JDK implementation**

`JdkHttpTransport.java`:
```java
package org.tvrenamer.controller.tvdb;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public class JdkHttpTransport implements TvdbV4Transport {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(TIMEOUT)
        .build();

    @Override
    public TvdbHttpResponse post(String url, String jsonBody, Map<String, String> headers)
        throws IOException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
            .timeout(TIMEOUT)
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        headers.forEach(b::header);
        return send(b.build());
    }

    @Override
    public TvdbHttpResponse get(String url, Map<String, String> headers) throws IOException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
            .timeout(TIMEOUT)
            .GET();
        headers.forEach(b::header);
        return send(b.build());
    }

    private TvdbHttpResponse send(HttpRequest req) throws IOException {
        try {
            HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString());
            return new TvdbHttpResponse(resp.statusCode(), resp.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests 'org.tvrenamer.controller.tvdb.JdkHttpTransportTest'`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/tvrenamer/controller/tvdb/TvdbV4Transport.java src/main/java/org/tvrenamer/controller/tvdb/JdkHttpTransport.java src/test/java/org/tvrenamer/controller/tvdb/JdkHttpTransportTest.java
git commit -m "feat: add v4 HTTP transport seam"
```

---

## Task 4: v4 client — login, token cache, 401 re-login+retry

**Files:**
- Create: `src/main/java/org/tvrenamer/controller/tvdb/TvdbV4Client.java`
- Test: `src/test/java/org/tvrenamer/controller/tvdb/TvdbV4ClientTest.java`

**Interfaces:**
- Consumes: `TvdbV4Transport`, `TvdbHttpResponse` (Task 3); `TVRenamerIOException` (existing model).
- Produces:
  - `new TvdbV4Client(TvdbV4Transport transport, java.util.function.Supplier<String> apiKeySupplier)`
  - `String searchSeriesJson(String query) throws TVRenamerIOException`
  - `String episodesJson(int seriesId, String seasonType, int page) throws TVRenamerIOException`
  - Base URL constant `https://api4.thetvdb.com/v4`.

- [ ] **Step 1: Write the failing test (fake transport records calls)**

```java
package org.tvrenamer.controller.tvdb;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
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

        @Override
        public TvdbHttpResponse post(String url, String body, Map<String, String> h) {
            calls.add("POST " + url);
            if (url.endsWith("/login")) {
                logins++;
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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'org.tvrenamer.controller.tvdb.TvdbV4ClientTest'`
Expected: FAIL — `TvdbV4Client` does not exist.

- [ ] **Step 3: Implement the client**

```java
package org.tvrenamer.controller.tvdb;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.tvrenamer.controller.tvdb.TvdbV4Transport.TvdbHttpResponse;
import org.tvrenamer.model.TVRenamerIOException;

/** Handles v4 auth (bearer token, reactive re-login) and raw JSON fetches. */
public class TvdbV4Client {

    static final String BASE_URL = "https://api4.thetvdb.com/v4";

    private static final Gson GSON = new Gson();

    private final TvdbV4Transport transport;
    private final Supplier<String> apiKeySupplier;
    private String token;

    public TvdbV4Client(TvdbV4Transport transport, Supplier<String> apiKeySupplier) {
        this.transport = transport;
        this.apiKeySupplier = apiKeySupplier;
    }

    public String searchSeriesJson(String query) throws TVRenamerIOException {
        String enc = URLEncoder.encode(query == null ? "" : query, StandardCharsets.UTF_8);
        return authedGet("/search?query=" + enc + "&type=series");
    }

    public String episodesJson(int seriesId, String seasonType, int page)
        throws TVRenamerIOException {
        return authedGet("/series/" + seriesId + "/episodes/" + seasonType + "?page=" + page);
    }

    private synchronized void login() throws TVRenamerIOException {
        String key = apiKeySupplier.get();
        if (key == null || key.trim().isEmpty()) {
            throw new TVRenamerIOException("TheTVDB v4 API key not configured");
        }
        Map<String, String> headers = jsonHeaders();
        String body = "{\"apikey\":\"" + key.trim() + "\"}";
        try {
            TvdbHttpResponse resp = transport.post(BASE_URL + "/login", body, headers);
            if (resp.status() != 200) {
                throw new TVRenamerIOException(
                    "TheTVDB v4 login failed (HTTP " + resp.status() + ")");
            }
            JsonObject o = GSON.fromJson(resp.body(), JsonObject.class);
            String t = (o != null && o.has("data"))
                ? o.getAsJsonObject("data").get("token").getAsString() : null;
            if (t == null || t.isEmpty()) {
                throw new TVRenamerIOException("TheTVDB v4 login returned no token");
            }
            token = t;
        } catch (IOException e) {
            throw new TVRenamerIOException("TheTVDB v4 login error: " + e.getMessage(), e);
        }
    }

    private String authedGet(String path) throws TVRenamerIOException {
        if (token == null) {
            login();
        }
        try {
            TvdbHttpResponse resp = transport.get(BASE_URL + path, authHeaders());
            if (resp.status() == 401) {
                // token expired: re-login once and retry
                login();
                resp = transport.get(BASE_URL + path, authHeaders());
            }
            if (resp.status() != 200) {
                throw new TVRenamerIOException(
                    "TheTVDB v4 request failed (HTTP " + resp.status() + "): " + path);
            }
            return resp.body();
        } catch (IOException e) {
            throw new TVRenamerIOException("TheTVDB v4 request error: " + e.getMessage(), e);
        }
    }

    private Map<String, String> jsonHeaders() {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Content-Type", "application/json");
        h.put("Accept", "application/json");
        return h;
    }

    private Map<String, String> authHeaders() {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Accept", "application/json");
        h.put("Authorization", "Bearer " + token);
        return h;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests 'org.tvrenamer.controller.tvdb.TvdbV4ClientTest'`
Expected: PASS (all 4 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/tvrenamer/controller/tvdb/TvdbV4Client.java src/test/java/org/tvrenamer/controller/tvdb/TvdbV4ClientTest.java
git commit -m "feat: add v4 client with token cache and 401 retry"
```

---

## Task 5: v4 parser — search results + episodes (pure functions)

**Files:**
- Create: `src/main/java/org/tvrenamer/controller/tvdb/V4Parser.java`
- Test: `src/test/java/org/tvrenamer/controller/tvdb/V4ParserTest.java`

**Interfaces:**
- Consumes: Gson; `EpisodeInfo` + `EpisodeInfo.Builder` (existing model, see `TheTVDBProvider.createEpisodeInfo` at `:287-302`).
- Produces:
  - `record V4SeriesResult(String tvdbId, String name, Integer year, java.util.List<String> aliases)`
  - `record V4EpisodesPage(java.util.List<EpisodeInfo> episodes, boolean hasNext)`
  - `static java.util.List<V4SeriesResult> parseSearchSeries(String json)`
  - `static V4EpisodesPage parseEpisodes(String json)`

- [ ] **Step 1: Write the failing test (fictional data)**

```java
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
        assertEquals("1", e.getSeasonNumber());
        assertEquals("8", e.getEpisodeNumber());
        assertEquals("The Quiet Ones", e.getEpisodeName());
    }

    @Test
    public void noNextLinkMeansLastPage() {
        String json = "{\"data\":{\"episodes\":[]},\"links\":{\"next\":null}}";
        assertFalse(V4Parser.parseEpisodes(json).hasNext());
    }
}
```

> NOTE: verify the `EpisodeInfo` getter names (`getSeasonNumber()`, `getEpisodeNumber()`, `getEpisodeName()`) against the actual class before finalizing; adjust the assertions to the real accessors if they differ. The builder setters are `episodeId/seasonNumber/episodeNumber/episodeName/firstAired` per `TheTVDBProvider.java:287-302`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'org.tvrenamer.controller.tvdb.V4ParserTest'`
Expected: FAIL — `V4Parser` does not exist.

- [ ] **Step 3: Implement the parser**

```java
package org.tvrenamer.controller.tvdb;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.tvrenamer.model.EpisodeInfo;

/** Pure JSON -> model parsing for v4 responses. No I/O. */
public final class V4Parser {

    private static final Gson GSON = new Gson();

    private V4Parser() {}

    public record V4SeriesResult(String tvdbId, String name, Integer year, List<String> aliases) {}

    public record V4EpisodesPage(List<EpisodeInfo> episodes, boolean hasNext) {}

    public static List<V4SeriesResult> parseSearchSeries(String json) {
        List<V4SeriesResult> out = new ArrayList<>();
        JsonObject root = GSON.fromJson(json, JsonObject.class);
        if (root == null || !root.has("data") || !root.get("data").isJsonArray()) {
            return out;
        }
        for (JsonElement el : root.getAsJsonArray("data")) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject o = el.getAsJsonObject();
            String id = str(o, "tvdb_id");
            String name = str(o, "name");
            if (id == null || name == null) {
                continue;
            }
            Integer year = null;
            String yr = str(o, "year");
            if (yr != null && yr.length() >= 4) {
                try {
                    year = Integer.parseInt(yr.substring(0, 4));
                } catch (NumberFormatException ignored) {
                    year = null;
                }
            }
            List<String> aliases = new ArrayList<>();
            if (o.has("aliases") && o.get("aliases").isJsonArray()) {
                for (JsonElement a : o.getAsJsonArray("aliases")) {
                    if (a.isJsonPrimitive()) {
                        aliases.add(a.getAsString());
                    }
                }
            }
            out.add(new V4SeriesResult(id, name, year, aliases));
        }
        return out;
    }

    public static V4EpisodesPage parseEpisodes(String json) {
        List<EpisodeInfo> episodes = new ArrayList<>();
        boolean hasNext = false;
        JsonObject root = GSON.fromJson(json, JsonObject.class);
        if (root != null && root.has("data") && root.get("data").isJsonObject()) {
            JsonObject data = root.getAsJsonObject("data");
            if (data.has("episodes") && data.get("episodes").isJsonArray()) {
                for (JsonElement el : data.getAsJsonArray("episodes")) {
                    if (!el.isJsonObject()) {
                        continue;
                    }
                    JsonObject e = el.getAsJsonObject();
                    String season = str(e, "seasonNumber");
                    String number = str(e, "number");
                    if (season == null || number == null) {
                        continue;
                    }
                    episodes.add(new EpisodeInfo.Builder()
                        .episodeId(str(e, "id"))
                        .seasonNumber(season)
                        .episodeNumber(number)
                        .episodeName(str(e, "name"))
                        .firstAired(str(e, "aired"))
                        .build());
                }
            }
        }
        if (root != null && root.has("links") && root.get("links").isJsonObject()) {
            JsonElement next = root.getAsJsonObject("links").get("next");
            hasNext = next != null && !next.isJsonNull();
        }
        return new V4EpisodesPage(episodes, hasNext);
    }

    /** Return a trimmed string for key, or null if absent/JSON-null/blank. */
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
}
```

- [ ] **Step 4: Reconcile EpisodeInfo accessors**

Open `src/main/java/org/tvrenamer/model/EpisodeInfo.java`, confirm the getter names used in the test (Step 1) and the builder setters used above compile. Fix the test assertions to the real getter names if different. Re-run:
Run: `./gradlew test --tests 'org.tvrenamer.controller.tvdb.V4ParserTest'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/tvrenamer/controller/tvdb/V4Parser.java src/test/java/org/tvrenamer/controller/tvdb/V4ParserTest.java
git commit -m "feat: add v4 JSON parser for search + episodes"
```

---

## Task 6: Provider interface, both implementations, selector, call-site swap

**Files:**
- Create: `controller/EpisodeDataProvider.java`, `controller/TheTVDBLegacyProvider.java`, `controller/TheTVDBv4Provider.java`, `controller/TvdbProviders.java`
- Modify: `model/ShowStore.java:468`, `controller/ListingsLookup.java:62`, `view/PreferencesDialog.java:2291,2320`
- Test: `src/test/java/org/tvrenamer/controller/TvdbProvidersTest.java`, `.../TheTVDBv4ProviderTest.java`

**Interfaces:**
- Consumes: `ShowName`, `Series`, `TVRenamerIOException`, `DiscontinuedApiException`, `TvdbV4Client`, `V4Parser`, `UserPreferences`, `EpisodeDataProviderType`.
- Produces:
  - `interface EpisodeDataProvider { void getShowOptions(ShowName) throws TVRenamerIOException, DiscontinuedApiException; void getSeriesListing(Series) throws TVRenamerIOException; }`
  - `TvdbProviders.current()` → `EpisodeDataProvider` (reads `UserPreferences.getInstance().getEpisodeDataProvider()`).
  - `TheTVDBv4Provider` with an injectable `TvdbV4Client` constructor for tests, plus a no-arg default constructor building a real client from prefs.

- [ ] **Step 1: Write the failing tests**

`TvdbProvidersTest.java`:
```java
package org.tvrenamer.controller;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.tvrenamer.model.EpisodeDataProviderType;
import org.tvrenamer.model.UserPreferences;

public class TvdbProvidersTest {
    @Test
    public void selectorFollowsPreference() {
        UserPreferences p = UserPreferences.getInstance();
        try {
            p.setEpisodeDataProvider(EpisodeDataProviderType.TVDB_V1);
            assertTrue(TvdbProviders.current() instanceof TheTVDBLegacyProvider);
            p.setEpisodeDataProvider(EpisodeDataProviderType.TVDB_V4);
            assertTrue(TvdbProviders.current() instanceof TheTVDBv4Provider);
        } finally {
            p.setEpisodeDataProvider(EpisodeDataProviderType.TVDB_V1);
        }
    }
}
```

`TheTVDBv4ProviderTest.java` (maps parsed results into the shared models via a fake client):
```java
package org.tvrenamer.controller;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.tvrenamer.controller.tvdb.TvdbV4Client;
import org.tvrenamer.controller.tvdb.TvdbV4Transport;
import org.tvrenamer.controller.tvdb.TvdbV4Transport.TvdbHttpResponse;
import org.tvrenamer.model.ShowName;
import org.tvrenamer.model.ShowOption;

public class TheTVDBv4ProviderTest {

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
}
```

> NOTE: confirm `ShowName.mapShowName(String)` and `ShowName.getShowOptions()`/`ShowOption.getIdString()` signatures (they are used at `ShowStore.java:414`, `ShowName.java:315`, `ShowOption.java:183`). Adjust if needed.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests 'org.tvrenamer.controller.TvdbProvidersTest' --tests 'org.tvrenamer.controller.TheTVDBv4ProviderTest'`
Expected: FAIL — types don't exist.

- [ ] **Step 3: Create the interface**

`EpisodeDataProvider.java`:
```java
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
```

- [ ] **Step 4: Create the legacy wrapper (v1 untouched)**

`TheTVDBLegacyProvider.java`:
```java
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
```

- [ ] **Step 5: Create the v4 provider**

`TheTVDBv4Provider.java`:
```java
package org.tvrenamer.controller;

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
        List<EpisodeInfo> episodes = fetchAll(series.getId(), preferDvd ? "dvd" : "default");
        if (episodes.isEmpty() && preferDvd) {
            // Series has no DVD ordering: fall back to aired order.
            episodes = fetchAll(series.getId(), "default");
        }
        // v4 ordering is baked into the chosen season-type; no per-episode DVD fallback.
        series.setPreferDvd(false);
        series.addEpisodeInfos(episodes.toArray(new EpisodeInfo[0]));
        series.listingsSucceeded();
    }

    private List<EpisodeInfo> fetchAll(int seriesId, String seasonType)
        throws TVRenamerIOException {
        java.util.ArrayList<EpisodeInfo> all = new java.util.ArrayList<>();
        int page = 0;
        boolean more = true;
        while (more && page < MAX_PAGES) {
            String json = client.episodesJson(seriesId, seasonType, page);
            V4EpisodesPage p = V4Parser.parseEpisodes(json);
            all.addAll(p.episodes());
            more = p.hasNext();
            page++;
        }
        return all;
    }
}
```

> NOTE: verify `Series.getId()`, `Series.setPreferDvd(boolean)`, `Series.addEpisodeInfos(EpisodeInfo[])`, `Series.listingsSucceeded()`, and `ShowName.addShowOption(String,String,Integer,List<String>)` signatures against the source (`TheTVDBProvider.java:103,163,358-363`). Adjust if the real signatures differ.

- [ ] **Step 6: Create the selector**

`TvdbProviders.java`:
```java
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
```

- [ ] **Step 7: Swap the 4 call sites**

- `model/ShowStore.java:468`: `TheTVDBProvider.getShowOptions(showName);` → `org.tvrenamer.controller.TvdbProviders.current().getShowOptions(showName);`
- `controller/ListingsLookup.java:62`: `TheTVDBProvider.getSeriesListing(series);` → `TvdbProviders.current().getSeriesListing(series);`
- `view/PreferencesDialog.java:2291` and `:2320`: `TheTVDBProvider.getShowOptions(sn);` → `TvdbProviders.current().getShowOptions(sn);`

(Add imports as needed; leave `TheTVDBProvider` imports where still referenced.)

- [ ] **Step 8: Run tests + full build**

Run: `./gradlew test --tests 'org.tvrenamer.controller.*'`
Then: `./gradlew build`
Expected: PASS; the selector test proves both branches; existing tests still green.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/org/tvrenamer/controller/EpisodeDataProvider.java src/main/java/org/tvrenamer/controller/TheTVDBLegacyProvider.java src/main/java/org/tvrenamer/controller/TheTVDBv4Provider.java src/main/java/org/tvrenamer/controller/TvdbProviders.java src/main/java/org/tvrenamer/model/ShowStore.java src/main/java/org/tvrenamer/controller/ListingsLookup.java src/main/java/org/tvrenamer/view/PreferencesDialog.java src/test/java/org/tvrenamer/controller/TvdbProvidersTest.java src/test/java/org/tvrenamer/controller/TheTVDBv4ProviderTest.java
git commit -m "feat: route lookups through a selectable EpisodeDataProvider"
```

---

## Task 7: Preferences UI — provider dropdown, API key field, Validate button

**Files:**
- Modify: `view/PreferencesDialog.java`, `model/util/Constants.java`
- Test: manual (SWT UI); the validation network call reuses tested code.

**Interfaces:**
- Consumes: `UserPreferences.get/setEpisodeDataProvider`, `get/setTvdbV4ApiKey` (Task 2); `TvdbV4Client` (Task 4) for the Validate check.

- [ ] **Step 1: Add UI label/tooltip constants**

In `model/util/Constants.java` (near the theme-mode label constants ~`:130-219`):
```java
    public static final String PROVIDER_LABEL_TEXT = "TV data provider:";
    public static final String PROVIDER_TOOLTIP =
        "Which TheTVDB API to use for looking up shows and episodes.";
    public static final String TVDB_V4_KEY_LABEL_TEXT = "TheTVDB v4 API key:";
    public static final String TVDB_V4_KEY_TOOLTIP =
        "Your personal TheTVDB v4 API key (required when the v4 provider is selected).";
    public static final String TVDB_V4_VALIDATE_BUTTON_TEXT = "Validate";
```

- [ ] **Step 2: Declare the widgets**

In `PreferencesDialog.java` (widget field area ~`:214-245`):
```java
    private Combo providerCombo;
    private Text tvdbV4KeyText;
    private Button tvdbV4ValidateButton;
    private Label tvdbV4ValidateStatus;
```

- [ ] **Step 3: Build the "TV data provider" group in `populateGeneralTab(...)`**

Add after the theme-mode block (`~:796-810`), following the 3-column grid convention:
```java
        createLabel(PROVIDER_LABEL_TEXT, PROVIDER_TOOLTIP, generalGroup);
        providerCombo = new Combo(generalGroup, SWT.DROP_DOWN | SWT.READ_ONLY);
        providerCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        providerCombo.setToolTipText(PROVIDER_TOOLTIP);
        for (EpisodeDataProviderType t : EpisodeDataProviderType.values()) {
            providerCombo.add(t.toString());
        }

        createLabel(TVDB_V4_KEY_LABEL_TEXT, TVDB_V4_KEY_TOOLTIP, generalGroup);
        tvdbV4KeyText = createText(prefs.getTvdbV4ApiKey(), generalGroup, false);
        tvdbV4ValidateButton = new Button(generalGroup, SWT.PUSH);
        tvdbV4ValidateButton.setText(TVDB_V4_VALIDATE_BUTTON_TEXT);

        tvdbV4ValidateStatus = new Label(generalGroup, SWT.NONE);
        tvdbV4ValidateStatus.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, true, false, 3, 1));

        tvdbV4ValidateButton.addListener(SWT.Selection, e -> validateTvdbV4KeyOnline());
        providerCombo.addListener(SWT.Selection, e -> updateProviderControlsEnabled());
```

- [ ] **Step 4: Load current values in `initializeGeneralControls()`**

Mirror the theme combo load (`~:850-859`):
```java
        int provIdx = providerCombo.indexOf(prefs.getEpisodeDataProvider().toString());
        providerCombo.select(provIdx >= 0 ? provIdx : 0);
        updateProviderControlsEnabled();
```
Add the helper (enables the key field/validate only for v4):
```java
    private void updateProviderControlsEnabled() {
        boolean v4 = EpisodeDataProviderType.TVDB_V4.toString()
            .equals(providerCombo.getText());
        tvdbV4KeyText.setEnabled(v4);
        tvdbV4ValidateButton.setEnabled(v4);
    }
```

- [ ] **Step 5: Read + commit in `savePreferences()`**

Alongside the theme write (`~:2050-2056`):
```java
        EpisodeDataProviderType provider =
            EpisodeDataProviderType.fromString(providerCombo.getText());
        prefs.setEpisodeDataProvider(
            provider == null ? EpisodeDataProviderType.TVDB_V1 : provider);
        prefs.setTvdbV4ApiKey(tvdbV4KeyText.getText());
```

- [ ] **Step 6: Add the async Validate method (mirror `validateMatchingRowOnline` at `:2157-2258`)**

```java
    private void validateTvdbV4KeyOnline() {
        final String key = tvdbV4KeyText.getText().trim();
        if (key.isEmpty()) {
            tvdbV4ValidateStatus.setText("Enter an API key first.");
            return;
        }
        tvdbV4ValidateStatus.setText("Validating…");
        Thread th = new Thread(() -> {
            boolean ok;
            String msg;
            try {
                TvdbV4Client c = new TvdbV4Client(new JdkHttpTransport(), () -> key);
                // A successful search implies login succeeded.
                c.searchSeriesJson("test");
                ok = true;
                msg = "API key is valid.";
            } catch (Exception ex) {
                ok = false;
                msg = "Validation failed: " + ex.getMessage();
            }
            final boolean fOk = ok;
            final String fMsg = msg;
            Display display = (preferencesShell != null)
                ? preferencesShell.getDisplay() : Display.getDefault();
            if (display == null || display.isDisposed()) {
                return;
            }
            display.asyncExec(() -> {
                if (tvdbV4ValidateStatus.isDisposed()) {
                    return;
                }
                tvdbV4ValidateStatus.setText((fOk ? "✓ " : "✗ ") + fMsg);
            });
        }, "tvrenamer-v4-validate");
        th.setDaemon(true);
        th.start();
    }
```
Add imports as needed: `org.tvrenamer.controller.tvdb.TvdbV4Client`, `org.tvrenamer.controller.tvdb.JdkHttpTransport`, and any missing SWT imports (`Combo`, `Text`, `Button`, `Label`, `Display`, `GridData`).

- [ ] **Step 7: Build packaging + manual verification**

Run: `./gradlew build shadowJar createExe`
Then launch `build/launch4j/TVRenamer.exe`:
- Preferences → General shows a "TV data provider" dropdown, key field, Validate button.
- Default is v1; key field disabled. Switch to v4 → key field enables.
- Paste a valid key → Validate → "✓ API key is valid." Invalid key → "✗ Validation failed…".
- Save; reopen Preferences → selection + key persisted (`~/.tvrenamer/prefs.xml`).

- [ ] **Step 8: Commit**

```bash
git add src/main/java/org/tvrenamer/view/PreferencesDialog.java src/main/java/org/tvrenamer/model/util/Constants.java
git commit -m "feat: preferences UI for provider selection + v4 key validation"
```

---

## Task 8: Re-match failed rows when the provider changes

**Files:**
- Modify: `view/ResultsTable.java` (the `updateUserPreferences` switch)
- Test: manual (view layer); relies on the unit-tested `rematchRows` engine from Completed.md #57.

**Interfaces:**
- Consumes: `UserPreference.EPISODE_DATA_PROVIDER` (Task 2); `rematchRows(Predicate<FileEpisode>)` + `FileEpisode.isShowUnfound()` (existing).

- [ ] **Step 1: Add the case**

In `ResultsTable.updateUserPreferences(UserPreference)`, add a case next to `SHOW_NAME_OVERRIDES`:
```java
            case EPISODE_DATA_PROVIDER:
                // Switching providers: retry rows that failed under the old provider.
                rematchRows(FileEpisode::isShowUnfound);
                break;
```
Add `TVDB_V4_API_KEY` to the no-op group (key change alone needs no re-match):
```java
            case TVDB_V4_API_KEY:
                // No table update needed on key change.
                break;
```

- [ ] **Step 2: Build**

Run: `./gradlew build`
Expected: PASS (switch now handles both new enum values; no "unhandled enum" warning-as-error if the codebase treats them as such).

- [ ] **Step 3: Manual verification**

Launch the EXE; with v1 selected and files showing "No info", open Preferences, switch to v4 (valid key), Save. The previously-unfound rows flip to `DOWNLOADING` and resolve.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/tvrenamer/view/ResultsTable.java
git commit -m "feat: re-match unresolved rows when the data provider changes"
```

---

## Task 9: Documentation — help page, README, Completed/TODO

**Files:**
- Modify: `src/main/resources/help/*.html` (the troubleshooting/FAQ page), `README*`, `docs/Completed.md`, `docs/TODO.md`

- [ ] **Step 1: Add a help-page troubleshooting note**

In the relevant help HTML page, add a section:
> **"No info" for a show you know exists.** The lookup provider's search may be
> temporarily degraded. In Preferences → General, switch **TV data provider**
> between TheTVDB (v1) and TheTVDB (v4). The v4 provider requires a personal API
> key (get one at thetvdb.com and paste it into Preferences, then click Validate).

- [ ] **Step 2: README note**

Add a short "Data providers" subsection describing the v1 default and the v4 option (key required, Validate button), and the "No info" troubleshooting pointer.

- [ ] **Step 3: Completed.md entry + TODO.md reconciliation**

Add a numbered `docs/Completed.md` entry (Title/Why/Where/What/Notes) summarizing the switchable provider. Remove any now-satisfied TODO items; if the v4 diagnosability nudge is deferred, note it in `docs/TODO.md`.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/help docs/Completed.md docs/TODO.md README*
git commit -m "docs: document selectable TheTVDB provider + v4 key setup"
```

---

## Final verification

- [ ] `./gradlew clean build shadowJar createExe` is green.
- [ ] Manual end-to-end (Windows), per the spec's Verification section: v1 default unchanged; v4 with a valid key resolves the previously-failing example files; Validate reports success/failure; toggle persists across restarts; DVD-order pref changes v4 numbering for a series that has DVD ordering.
- [ ] `-Dtvrenamer.debug=true`: confirm v4 logs one `/login`, reuses the token, and searches by name.

---

## Self-review notes (author)

- **Spec coverage:** interface+selector (Task 6) ✓; v4 auth/search/episodes (Tasks 4–6) ✓; prefs+UI+validate (Tasks 2,7) ✓; DVD-order via existing pref (Task 6) ✓; Gson dep (Task 1) ✓; rematch on switch (Task 8) ✓; help/docs note (Task 9) ✓; verified response shapes honored in Task 5 parser ✓.
- **Assumptions to verify while implementing (flagged inline):** exact `EpisodeInfo` accessor names (Task 5 Step 4), and `Series`/`ShowName`/`ShowOption` method signatures (Task 6 Step 5 NOTE). These are the only places the plan depends on accessor names not directly quoted from the spec.
- **Type consistency:** `EpisodeDataProviderType`, `TvdbV4Client`, `V4Parser.V4SeriesResult/V4EpisodesPage`, `TvdbHttpResponse`, `EpisodeDataProvider` names are used identically across tasks.
