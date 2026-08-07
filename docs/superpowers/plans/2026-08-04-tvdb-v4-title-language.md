# TVDB v4 Title Language Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an operator-selectable **Title language** (default English, v4-only) that governs the language of the show name (`%S`) and episode titles (`%t`) written into renamed files.

**Architecture:** Episode titles are fetched from `/series/{id}/episodes/{season-type}/{lang}` (translated `name`, per-episode fallback). The show name is fetched from `/series/{id}/translations/{lang}` and applied via a new nullable display-name override on `Show`/`ShowOption` that `getName()` returns when set. A `TitleLanguage` preference (default English) drives both; v1 is unaffected.

**Tech Stack:** Java 17 / JDK 21 toolchain, SWT, Gradle, JUnit Jupiter, Gson, `java.net.http`.

**Spec:** `docs/TVDB v4 Title Language Spec.md` (verified live v4 shapes 2026-08-04).

## Global Constraints

- Java 17 bytecode target; dependency versions only in `gradle/libs.versions.toml` (no new deps expected here).
- **No real show/service/scene names** in code, tests, or docs (naming "TheTVDB" is allowed). Test JSON uses fictional names (e.g. a series with Spanish primary name "Ciudad del Sol" and English translation "Sun City").
- v1 `TheTVDBProvider.java` stays unchanged; the Title-language setting is v4-only.
- Default Title language is **English (`eng`)**.
- Never produce an empty `%S`/`%t` — always fall back to the primary/default name.
- 4-space indent; no LF/CRLF churn. `./gradlew build` after each task; `shadowJar createExe` when UI changes.
- Preferences use hand-rolled XML persistence: a new pref must be registered in the constructor default, `UserPreferences.fromParsedXml`, `UserPreferencesPersistence.persist()` + `SCALAR_FIELDS`, and get a `UserPreference` enum value.

---

## File structure

**New**
- `src/main/java/org/tvrenamer/model/TitleLanguage.java` — enum (display name + ISO 639-2 code).
- Tests under `src/test/java/org/tvrenamer/model/` and `.../controller/tvdb/` and `.../controller/`.

**Modified**
- `model/UserPreferences.java`, `controller/UserPreferencesPersistence.java`, `model/UserPreference.java` — the pref.
- `controller/tvdb/TvdbV4Client.java` — language episodes URL + series-translation call.
- `controller/tvdb/V4Parser.java` — parse the translation `name`.
- `model/ShowOption.java` — display-name override.
- `controller/TheTVDBv4Provider.java` — thread language into listings + set the override.
- `view/PreferencesDialog.java`, `model/util/Constants.java` — the dropdown.
- `view/ResultsTable.java` — `TITLE_LANGUAGE` no-op case.
- `src/main/resources/help/*.html`, `README.md`, `docs/Completed.md`, `docs/TODO.md` — docs.

---

## Task 1: TitleLanguage enum + titleLanguage preference

**Files:**
- Create: `src/main/java/org/tvrenamer/model/TitleLanguage.java`
- Modify: `model/UserPreferences.java`, `model/UserPreference.java`, `controller/UserPreferencesPersistence.java`
- Test: `src/test/java/org/tvrenamer/model/TitleLanguageTest.java`

**Interfaces:**
- Produces: `TitleLanguage` enum with `toString()` (display), `String code()` (3-letter), `static TitleLanguage fromString(String)`; `UserPreferences.getTitleLanguage()`/`setTitleLanguage(TitleLanguage)`; `UserPreference.TITLE_LANGUAGE`.

- [ ] **Step 1: Write the failing test**

```java
package org.tvrenamer.model;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.HashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tvrenamer.controller.UserPreferencesPersistence;

public class TitleLanguageTest {

    @AfterEach
    public void reset() {
        UserPreferences.getInstance().setTitleLanguage(TitleLanguage.ENGLISH);
    }

    @Test
    public void codeAndFromStringRoundTrip() {
        assertEquals("spa", TitleLanguage.SPANISH.code());
        assertEquals(TitleLanguage.SPANISH, TitleLanguage.fromString("Spanish"));
        assertEquals(TitleLanguage.SPANISH, TitleLanguage.fromString("spanish"));
        assertNull(TitleLanguage.fromString("nonsense"));
        assertEquals("eng", TitleLanguage.ENGLISH.code());
    }

    @Test
    public void defaultIsEnglish() {
        UserPreferences defaults = UserPreferences.fromParsedXml(
            new HashMap<>(), java.util.List.of(), new HashMap<>(), new HashMap<>());
        assertEquals(TitleLanguage.ENGLISH, defaults.getTitleLanguage());
    }

    @Test
    public void persistenceRoundTrip(@TempDir Path dir) {
        Path file = dir.resolve("prefs.xml");
        UserPreferences p = UserPreferences.getInstance();
        p.setTitleLanguage(TitleLanguage.JAPANESE);
        UserPreferencesPersistence.persist(p, file);
        UserPreferences read = UserPreferencesPersistence.retrieve(file);
        assertEquals(TitleLanguage.JAPANESE, read.getTitleLanguage());
    }
}
```

> NOTE: confirm the exact `UserPreferences.fromParsedXml(...)` parameter types against the source (used the same way in `ProviderPreferencesTest`); adjust the empty-collection literals if the keywords/overrides types differ.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'org.tvrenamer.model.TitleLanguageTest'`
Expected: FAIL — `TitleLanguage` and the getter don't exist.

- [ ] **Step 3: Create the enum**

`src/main/java/org/tvrenamer/model/TitleLanguage.java`:
```java
package org.tvrenamer.model;

import java.util.Locale;

/** Language used for the show name and episode titles written into renamed files (v4 provider). */
public enum TitleLanguage {
    ENGLISH("English", "eng"),
    SPANISH("Spanish", "spa"),
    FRENCH("French", "fra"),
    GERMAN("German", "deu"),
    ITALIAN("Italian", "ita"),
    PORTUGUESE("Portuguese", "por"),
    DUTCH("Dutch", "nld"),
    RUSSIAN("Russian", "rus"),
    JAPANESE("Japanese", "jpn"),
    KOREAN("Korean", "kor"),
    CHINESE("Chinese", "zho"),
    ARABIC("Arabic", "ara"),
    SWEDISH("Swedish", "swe"),
    POLISH("Polish", "pol"),
    TURKISH("Turkish", "tur");

    private final String label;
    private final String code;

    TitleLanguage(String label, String code) {
        this.label = label;
        this.code = code;
    }

    /** ISO 639-2/T code sent to the TheTVDB v4 API (e.g. "eng"). */
    public String code() {
        return code;
    }

    @Override
    public String toString() {
        return label;
    }

    public static TitleLanguage fromString(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        if (v.isEmpty()) {
            return null;
        }
        String upper = v.toUpperCase(Locale.ROOT);
        for (TitleLanguage t : values()) {
            if (t.name().equals(upper)
                || t.label.toUpperCase(Locale.ROOT).equals(upper)
                || t.code.equalsIgnoreCase(v)) {
                return t;
            }
        }
        return null;
    }
}
```

- [ ] **Step 4: Add `TITLE_LANGUAGE` to `UserPreference.java`**

In the enum body (near `TVDB_V4_API_KEY`), add:
```java
    TITLE_LANGUAGE,
```

- [ ] **Step 5: Field/default/getter/setter in `UserPreferences.java`**

Field (near `tvdbV4ApiKey`):
```java
    private TitleLanguage titleLanguage;
```
Default in the constructor (near `tvdbV4ApiKey = "";`):
```java
        titleLanguage = TitleLanguage.ENGLISH;
```
Getter + setter (mirror `getThemeMode`/`setThemeMode`):
```java
    public TitleLanguage getTitleLanguage() {
        return titleLanguage;
    }

    public void setTitleLanguage(TitleLanguage language) {
        TitleLanguage resolved = (language == null) ? TitleLanguage.ENGLISH : language;
        if (valuesAreDifferent(this.titleLanguage, resolved)) {
            this.titleLanguage = resolved;
            preferenceChanged(UserPreference.TITLE_LANGUAGE);
        }
    }
```
Deserialize block in `fromParsedXml(...)` (mirror the `themeMode` block):
```java
        val = scalars.get("titleLanguage");
        if (val != null) {
            TitleLanguage t = TitleLanguage.fromString(val);
            p.titleLanguage = (t == null) ? TitleLanguage.ENGLISH : t;
        }
```

- [ ] **Step 6: Register persistence in `UserPreferencesPersistence.java`**

Add to `SCALAR_FIELDS[]`:
```java
        "titleLanguage",
```
Add to `persist(...)` (near the `episodeDataProvider` line):
```java
        appendElement(xml, "titleLanguage", prefs.getTitleLanguage().name());
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew test --tests 'org.tvrenamer.model.TitleLanguageTest'` then `./gradlew build`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/org/tvrenamer/model/TitleLanguage.java src/main/java/org/tvrenamer/model/UserPreferences.java src/main/java/org/tvrenamer/model/UserPreference.java src/main/java/org/tvrenamer/controller/UserPreferencesPersistence.java src/test/java/org/tvrenamer/model/TitleLanguageTest.java
git commit -m "feat: add TitleLanguage enum + titleLanguage preference"
```

---

## Task 2: v4 client — language episodes URL + series-translation call + parser

**Files:**
- Modify: `controller/tvdb/TvdbV4Client.java`, `controller/tvdb/V4Parser.java`, `controller/TheTVDBv4Provider.java` (caller signature only)
- Test: `src/test/java/org/tvrenamer/controller/tvdb/TvdbV4ClientTest.java` (extend), `.../V4ParserTest.java` (extend)

**Interfaces:**
- Consumes: `TvdbV4Transport` fake (existing test seam).
- Produces:
  - `TvdbV4Client.episodesJson(int seriesId, String seasonType, String lang, int page)` — URL `/series/{id}/episodes/{seasonType}/{lang}?page=N` when `lang != null`, else `/series/{id}/episodes/{seasonType}?page=N`.
  - `TvdbV4Client.seriesTranslationJson(int seriesId, String lang)` — URL `/series/{id}/translations/{lang}`.
  - `V4Parser.parseTranslationName(String json)` — returns `data.name` (String) or null.

- [ ] **Step 1: Write the failing tests**

Add to `TvdbV4ClientTest.java` (the FakeTransport already records `calls` with the requested URL):
```java
    @Test
    public void episodesUrlIncludesLanguageSegmentWhenProvided() throws Exception {
        FakeTransport t = new FakeTransport();
        t.getQueue.add(new TvdbHttpResponse(200, "{\"data\":{\"episodes\":[]}}"));
        TvdbV4Client c = new TvdbV4Client(t, () -> "key");
        c.episodesJson(555, "default", "eng", 0);
        assertTrue(t.calls.stream().anyMatch(
            s -> s.contains("/series/555/episodes/default/eng?page=0")),
            "expected language segment in episodes URL; calls=" + t.calls);
    }

    @Test
    public void episodesUrlOmitsLanguageSegmentWhenNull() throws Exception {
        FakeTransport t = new FakeTransport();
        t.getQueue.add(new TvdbHttpResponse(200, "{\"data\":{\"episodes\":[]}}"));
        TvdbV4Client c = new TvdbV4Client(t, () -> "key");
        c.episodesJson(555, "default", null, 0);
        assertTrue(t.calls.stream().anyMatch(
            s -> s.contains("/series/555/episodes/default?page=0")
              && !s.contains("/default/")),
            "expected no language segment; calls=" + t.calls);
    }

    @Test
    public void seriesTranslationUrlIsCorrect() throws Exception {
        FakeTransport t = new FakeTransport();
        t.getQueue.add(new TvdbHttpResponse(200, "{\"data\":{\"name\":\"Sun City\",\"language\":\"eng\"}}"));
        TvdbV4Client c = new TvdbV4Client(t, () -> "key");
        String json = c.seriesTranslationJson(555, "eng");
        assertTrue(t.calls.stream().anyMatch(s -> s.contains("/series/555/translations/eng")),
            "calls=" + t.calls);
        assertTrue(json.contains("Sun City"));
    }
```

Add to `V4ParserTest.java`:
```java
    @Test
    public void parsesTranslationName() {
        String json = "{\"status\":\"success\",\"data\":{\"name\":\"Sun City\",\"language\":\"eng\"}}";
        assertEquals("Sun City", V4Parser.parseTranslationName(json));
    }

    @Test
    public void translationNameNullWhenAbsent() {
        assertNull(V4Parser.parseTranslationName("{\"status\":\"success\",\"data\":{\"language\":\"eng\"}}"));
        assertNull(V4Parser.parseTranslationName("{\"data\":null}"));
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests 'org.tvrenamer.controller.tvdb.TvdbV4ClientTest' --tests 'org.tvrenamer.controller.tvdb.V4ParserTest'`
Expected: FAIL — new method signatures/`parseTranslationName` missing.

- [ ] **Step 3: Update `TvdbV4Client.episodesJson` + add `seriesTranslationJson`**

Replace the existing `episodesJson(int, String, int)` with:
```java
    public String episodesJson(int seriesId, String seasonType, String lang, int page)
        throws TVRenamerIOException {
        String base = "/series/" + seriesId + "/episodes/" + seasonType;
        if (lang != null && !lang.isBlank()) {
            base = base + "/" + lang;
        }
        return authedGet(base + "?page=" + page);
    }

    public String seriesTranslationJson(int seriesId, String lang)
        throws TVRenamerIOException {
        return authedGet("/series/" + seriesId + "/translations/" + lang);
    }
```

- [ ] **Step 4: Update the existing caller so the build stays green**

In `TheTVDBv4Provider.java`, the current `fetchAll` calls `client.episodesJson(seriesId, seasonType, page)`. Update that single call to pass `null` for the new language argument for now (behaviour unchanged — Task 4 threads the real language through):
```java
            String json = client.episodesJson(seriesId, seasonType, null, page);
```

- [ ] **Step 5: Add `V4Parser.parseTranslationName`**

In `V4Parser.java` (uses the existing private `str(JsonObject, String)` helper and `GSON`):
```java
    /** Parse the translated series name from a v4 /series/{id}/translations/{lang} response. */
    public static String parseTranslationName(String json) {
        JsonObject root = GSON.fromJson(json, JsonObject.class);
        if (root == null || !root.has("data") || !root.get("data").isJsonObject()) {
            return null;
        }
        return str(root.getAsJsonObject("data"), "name");
    }
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew test --tests 'org.tvrenamer.controller.tvdb.*'` then `./gradlew build`
Expected: PASS (new + existing client/parser tests green; provider still compiles).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/org/tvrenamer/controller/tvdb/TvdbV4Client.java src/main/java/org/tvrenamer/controller/tvdb/V4Parser.java src/main/java/org/tvrenamer/controller/TheTVDBv4Provider.java src/test/java/org/tvrenamer/controller/tvdb/TvdbV4ClientTest.java src/test/java/org/tvrenamer/controller/tvdb/V4ParserTest.java
git commit -m "feat: v4 client language episodes URL + series-translation fetch"
```

---

## Task 3: display-name override on ShowOption

**Files:**
- Modify: `model/ShowOption.java`
- Test: `src/test/java/org/tvrenamer/model/ShowOptionDisplayNameTest.java`

**Interfaces:**
- Produces: `ShowOption.setDisplayNameOverride(String)`; `getName()` returns the override when set (non-null/non-blank), otherwise the original `name`. Inherited by `Show`/`Series`.

- [ ] **Step 1: Write the failing test**

```java
package org.tvrenamer.model;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class ShowOptionDisplayNameTest {
    private static final AtomicInteger ID = new AtomicInteger(880001);

    @Test
    public void getNameReturnsOverrideWhenSet() {
        Series s = Series.createSeries(ID.getAndIncrement(), "Ciudad del Sol");
        assertEquals("Ciudad del Sol", s.getName());
        s.setDisplayNameOverride("Sun City");
        assertEquals("Sun City", s.getName());
    }

    @Test
    public void blankOrNullOverrideFallsBackToOriginal() {
        Series s = Series.createSeries(ID.getAndIncrement(), "Ciudad del Sol");
        s.setDisplayNameOverride("Sun City");
        s.setDisplayNameOverride(null);
        assertEquals("Ciudad del Sol", s.getName());
        s.setDisplayNameOverride("   ");
        assertEquals("Ciudad del Sol", s.getName());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'org.tvrenamer.model.ShowOptionDisplayNameTest'`
Expected: FAIL — `setDisplayNameOverride` doesn't exist.

- [ ] **Step 3: Implement the override in `ShowOption.java`**

Add a field near `final String name;`:
```java
    private volatile String displayNameOverride = null;
```
Add the setter:
```java
    /**
     * Override the name returned by {@link #getName()} (e.g. a translated series
     * name). Pass null/blank to clear and fall back to the original name.
     */
    public void setDisplayNameOverride(String override) {
        this.displayNameOverride = override;
    }
```
Change `getName()` to prefer the override:
```java
    public String getName() {
        String o = displayNameOverride;
        if (o != null && !o.isBlank()) {
            return o;
        }
        return name;
    }
```

> NOTE: `getName()` currently just `return name;`. Preserve any surrounding javadoc.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests 'org.tvrenamer.model.ShowOptionDisplayNameTest'` then `./gradlew build`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/tvrenamer/model/ShowOption.java src/test/java/org/tvrenamer/model/ShowOptionDisplayNameTest.java
git commit -m "feat: add display-name override to ShowOption (for translated names)"
```

---

## Task 4: wire the language into TheTVDBv4Provider

**Files:**
- Modify: `controller/TheTVDBv4Provider.java`
- Test: `src/test/java/org/tvrenamer/controller/TheTVDBv4ProviderTest.java` (extend)

**Interfaces:**
- Consumes: `TvdbV4Client.episodesJson(id, seasonType, lang, page)` + `seriesTranslationJson(id, lang)` (Task 2); `V4Parser.parseTranslationName` (Task 2); `ShowOption.setDisplayNameOverride` (Task 3); `UserPreferences.getTitleLanguage().code()` (Task 1).
- Produces: `getSeriesListing` fetches episodes in the preferred language (fallback to no-language on error) and sets the series display-name override from the translated name (cleared when unavailable).

- [ ] **Step 1: Write the failing tests (fake client + fictional data)**

Extend `TheTVDBv4ProviderTest.java`. Use a transport that answers by URL substring so both the episodes and translations calls can be scripted:
```java
    private static TvdbV4Client scriptedClient(java.util.function.Function<String,String> byUrl) {
        TvdbV4Transport t = new TvdbV4Transport() {
            public TvdbHttpResponse post(String u, String b, java.util.Map<String,String> h) {
                return new TvdbHttpResponse(200, "{\"data\":{\"token\":\"tok\"}}");
            }
            public TvdbHttpResponse get(String u, java.util.Map<String,String> h) {
                return new TvdbHttpResponse(200, byUrl.apply(u));
            }
        };
        return new TvdbV4Client(t, () -> "key");
    }

    @Test
    public void setsTranslatedSeriesNameOverride() throws Exception {
        UserPreferences.getInstance().setTitleLanguage(TitleLanguage.ENGLISH);
        try {
            TvdbV4Client c = scriptedClient(u -> {
                if (u.contains("/translations/")) {
                    return "{\"data\":{\"name\":\"Sun City\",\"language\":\"eng\"}}";
                }
                return "{\"data\":{\"episodes\":[]}}";
            });
            TheTVDBv4Provider provider = new TheTVDBv4Provider(c);
            Series s = Series.createSeries(770101, "Ciudad del Sol");
            provider.getSeriesListing(s);
            assertEquals("Sun City", s.getName());
        } finally {
            UserPreferences.getInstance().setTitleLanguage(TitleLanguage.ENGLISH);
        }
    }

    @Test
    public void clearsOverrideWhenTranslationMissing() throws Exception {
        TvdbV4Client c = scriptedClient(u ->
            u.contains("/translations/")
                ? "{\"data\":{\"language\":\"eng\"}}"     // no name
                : "{\"data\":{\"episodes\":[]}}");
        TheTVDBv4Provider provider = new TheTVDBv4Provider(c);
        Series s = Series.createSeries(770102, "Ciudad del Sol");
        s.setDisplayNameOverride("stale");
        provider.getSeriesListing(s);
        assertEquals("Ciudad del Sol", s.getName(), "override must reset when no translation");
    }
```

> NOTE: confirm `TheTVDBv4ProviderTest` already imports `TvdbV4Transport`/`TvdbHttpResponse`/`Series`/`UserPreferences`/`TitleLanguage`; add imports as needed.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests 'org.tvrenamer.controller.TheTVDBv4ProviderTest'`
Expected: FAIL — provider doesn't fetch translations or thread language yet.

- [ ] **Step 3: Implement in `TheTVDBv4Provider.getSeriesListing` + `fetchAll`**

Replace `getSeriesListing` and `fetchAll` with language-aware versions (keep the DVD→default season-type fallback; add a language fallback inside `fetchAll`; reset-then-set the override):
```java
    @Override
    public void getSeriesListing(Series series) throws TVRenamerIOException {
        final int id = series.getId();
        final String lang = UserPreferences.getInstance().getTitleLanguage().code();

        // Series name: reset first (determinism across cache re-use), then apply the
        // translated name for the chosen language if one is available.
        series.setDisplayNameOverride(null);
        try {
            String translated = V4Parser.parseTranslationName(
                client.seriesTranslationJson(id, lang));
            if (translated != null && !translated.isBlank()) {
                series.setDisplayNameOverride(translated);
            }
        } catch (TVRenamerIOException e) {
            // No translation available for this language: keep the original name.
            logger.fine("v4 series translation unavailable for " + id + "/" + lang
                + ": " + e.getMessage());
        }

        // Episodes: prefer DVD ordering when requested, else aired; language applied
        // to whichever season-type is used.
        boolean preferDvd = UserPreferences.getInstance().isPreferDvdOrderIfPresent();
        List<EpisodeInfo> episodes;
        if (preferDvd) {
            List<EpisodeInfo> dvd;
            try {
                dvd = fetchAll(id, "dvd", lang);
            } catch (TVRenamerIOException e) {
                dvd = java.util.Collections.emptyList();
            }
            episodes = dvd.isEmpty() ? fetchAll(id, "default", lang) : dvd;
        } else {
            episodes = fetchAll(id, "default", lang);
        }

        series.setPreferDvd(false);
        series.addEpisodeInfos(episodes.toArray(new EpisodeInfo[0]));
        series.listingsSucceeded();
    }

    private List<EpisodeInfo> fetchAll(int id, String seasonType, String lang)
        throws TVRenamerIOException {
        try {
            return fetchPages(id, seasonType, lang);
        } catch (TVRenamerIOException e) {
            if (lang != null) {
                // Language-qualified request failed: retry the same season-type
                // without a language segment (default-language titles).
                return fetchPages(id, seasonType, null);
            }
            throw e;
        }
    }

    private List<EpisodeInfo> fetchPages(int id, String seasonType, String lang)
        throws TVRenamerIOException {
        java.util.ArrayList<EpisodeInfo> all = new java.util.ArrayList<>();
        int page = 0;
        boolean more = true;
        while (more && page < MAX_PAGES) {
            V4Parser.V4EpisodesPage p =
                V4Parser.parseEpisodes(client.episodesJson(id, seasonType, lang, page));
            all.addAll(p.episodes());
            more = p.hasNext();
            page++;
        }
        return all;
    }
```

> NOTE: this replaces the Task-2 temporary `episodesJson(id, seasonType, null, page)` call with the language-threaded `fetchPages`. Keep the existing `MAX_PAGES` constant and imports; add `import java.util.List;`/`Collections` if not present. Verify `logger` exists on the class (add a `java.util.logging.Logger` if the class doesn't already have one).

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests 'org.tvrenamer.controller.TheTVDBv4ProviderTest'` then `./gradlew build`
Expected: PASS (new + existing provider tests green).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/tvrenamer/controller/TheTVDBv4Provider.java src/test/java/org/tvrenamer/controller/TheTVDBv4ProviderTest.java
git commit -m "feat: fetch v4 titles in the preferred language + translated series name"
```

---

## Task 5: Preferences UI — Title language dropdown

**Files:**
- Modify: `view/PreferencesDialog.java`, `model/util/Constants.java`, `view/ResultsTable.java`
- Test: manual (SWT); build + package must succeed.

**Interfaces:**
- Consumes: `TitleLanguage` (Task 1); `UserPreferences.get/setTitleLanguage` (Task 1).

- [ ] **Step 1: Add label/tooltip constants**

In `Constants.java` (near the provider constants added earlier):
```java
    public static final String TITLE_LANGUAGE_LABEL_TEXT = "Title language:";
    public static final String TITLE_LANGUAGE_TOOLTIP =
        "Language for the show name and episode titles in renamed files (TheTVDB v4 only).";
```

- [ ] **Step 2: Declare the widget**

In `PreferencesDialog.java` (near `providerCombo`):
```java
    private Combo titleLanguageCombo;
```

- [ ] **Step 3: Build it in the "TV data provider" group (in `populateGeneralTab`)**

Add after the provider dropdown block:
```java
        createLabel(TITLE_LANGUAGE_LABEL_TEXT, TITLE_LANGUAGE_TOOLTIP, generalGroup);
        titleLanguageCombo = new Combo(generalGroup, SWT.DROP_DOWN | SWT.READ_ONLY);
        titleLanguageCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        titleLanguageCombo.setToolTipText(TITLE_LANGUAGE_TOOLTIP);
        for (TitleLanguage t : TitleLanguage.values()) {
            titleLanguageCombo.add(t.toString());
        }
        ThemeManager.applyPalette(titleLanguageCombo, themePalette);
```

- [ ] **Step 4: Load current value + enable-only-for-v4**

In `initializeGeneralControls()` (near where the provider combo is selected):
```java
        int langIdx = titleLanguageCombo.indexOf(prefs.getTitleLanguage().toString());
        titleLanguageCombo.select(langIdx >= 0 ? langIdx : 0);
```
In `updateProviderControlsEnabled()` add (v4-only, alongside the key field):
```java
        titleLanguageCombo.setEnabled(v4);
```

- [ ] **Step 5: Read + commit in `savePreferences()`**

Near the provider commit:
```java
        TitleLanguage titleLang = TitleLanguage.fromString(titleLanguageCombo.getText());
        prefs.setTitleLanguage(titleLang == null ? TitleLanguage.ENGLISH : titleLang);
```

- [ ] **Step 6: Add the `TITLE_LANGUAGE` no-op case in `ResultsTable.updateUserPreferences`**

Add `TITLE_LANGUAGE` to the existing no-op case group (it applies to subsequently-matched files; no table update), next to `TVDB_V4_API_KEY`:
```java
            case TITLE_LANGUAGE:
                // Applies to files matched after the change; no immediate table update.
                break;
```

- [ ] **Step 7: Build + package + manual verification**

Run: `./gradlew build shadowJar createExe`
Then launch the EXE: Preferences → General shows a **Title language** dropdown under the provider settings; it's enabled only when the v4 provider is selected; selection persists across restart (`~/.tvrenamer/prefs.xml` has `<titleLanguage>`).

- [ ] **Step 8: Commit**

```bash
git add src/main/java/org/tvrenamer/view/PreferencesDialog.java src/main/java/org/tvrenamer/model/util/Constants.java src/main/java/org/tvrenamer/view/ResultsTable.java
git commit -m "feat: Title language dropdown in preferences (v4 only)"
```

---

## Task 6: Documentation

**Files:**
- Modify: `src/main/resources/help/preferences.html`, `src/main/resources/help/troubleshooting.html`, `README.md`, `docs/Completed.md`, `docs/TODO.md`

- [ ] **Step 1: Help — preferences.html**

Document the **Title language** setting in the TV-data-provider section: it sets the language of the show name and episode titles under the v4 provider (default English), falls back to the show's original language when a translation isn't available, and is disabled under the v1 provider (v1 is English-only). Note that some shows have a translated show name but only original-language episode titles (TheTVDB data completeness).

- [ ] **Step 2: Help — troubleshooting.html**

Add a line: if titles come out in an unexpected language, check Preferences → General → Title language (v4 only).

- [ ] **Step 3: README**

Add a short note under the data-providers section: v4 supports a selectable Title language for the output show name and episode titles; default English.

- [ ] **Step 4: Completed.md + TODO.md**

Add the next numbered `docs/Completed.md` entry (check the current highest — #58 exists; use the next) with Title/Why/Where/What/Notes summarizing the feature. In `docs/TODO.md`, add the deferred enhancement: "Live retroactive re-translation of already-loaded rows when Title language changes (needs Series listings-cache + display-name-override invalidation)."

- [ ] **Step 5: Build + commit**

```bash
./gradlew build
git add src/main/resources/help docs/Completed.md docs/TODO.md README.md
git commit -m "docs: document v4 Title language setting"
```

---

## Final verification

- [ ] `./gradlew clean build shadowJar createExe` green.
- [ ] Manual (Windows, per spec Verification): with v4 selected and a real key, set Title language = English and add a non-English-primary show → `%S` and `%t` render in English. Switch to the show's primary language → original titles. Switch to a language the show lacks → graceful fallback (no blank). Confirm the dropdown is disabled under v1.
- [ ] `-Dtvrenamer.debug=true`: episodes request carries the language segment; one translations call per resolved series.

---

## Self-review notes (author)

- **Spec coverage:** enum + pref (Task 1); episode-language URL + translation fetch + parser (Task 2); display-name override (Task 3); provider wiring + fallbacks + reset (Task 4); UI + no-op re-match case (Task 5); docs incl. deferred live-refresh (Task 6). All spec sections covered.
- **Assumptions flagged inline:** exact `fromParsedXml(...)` parameter types (Task 1), and presence of a class `logger` in `TheTVDBv4Provider` (Task 4).
- **Type consistency:** `TitleLanguage.code()`/`fromString`, `episodesJson(id,seasonType,lang,page)`, `seriesTranslationJson(id,lang)`, `V4Parser.parseTranslationName`, `ShowOption.setDisplayNameOverride`/`getName` used identically across tasks. Task 2 keeps the build green by passing `null` from the provider until Task 4 threads the real language.
