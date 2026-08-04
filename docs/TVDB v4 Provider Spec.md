# TVDB v4 Provider — Design Spec

## Context

TVRenamer resolves episodes through **TheTVDB v1 XML API** (`thetvdb.com/api/`).
As of 2026-08-04 that API's **name-search index is returning empty results for
every query**: `GetSeries.php?seriesname=<anything>` responds `HTTP 200` with an
empty `<Data></Data>` envelope. This is *not* a decommission — direct v1 lookups
by known ID (`/series/{id}/en.xml`, `/series/{id}/all/en.xml`) and remote-ID
lookups (`GetSeriesByRemoteID.php`) still return full, correct data. Only the
name→ID search is broken, and it fails silently (no `Sunset`/`Deprecation`
header, no error status).

Because TVRenamer only has the show *name* parsed from a filename, the broken
name→ID search makes every lookup fail with a misleading per-row "No info for
X". Waiting for TheTVDB to fix the index is possible but open-ended.

**Goal:** add **TheTVDB v4 REST/JSON API** as a second, operator-selectable
provider so lookups work while v1 search is down, without removing v1 (which is
zero-config and preferred if/when its search recovers).

### Reference implementations studied
- **ComPlexionist** (Python, `httpx`+`pydantic`) and **Parrot** (TypeScript)
  both use v4 with the same auth flow. Neither implements **search** (both get
  the series ID by other means), so the v4 search path is net-new here.

## Non-goals / out of scope
- Removing or changing the v1 provider's behaviour ("keep v1 as-is").
- Automatic fallback between providers — this is a **pure manual switch**.
- Localisation beyond English.
- Fixing v1's empty-search diagnosability in code (handled via a help-page note).

## Decisions (settled with the user)
- **Manual toggle only.** The selected provider is the only one used.
- **v1 is the default** (keyless). v4 requires a user-supplied API key.
- **JSON library: Gson** (new runtime dependency; small, stable, fits flat DTOs).
- **DVD ordering reuses the existing `preferDvdOrderIfPresent` preference** — no
  new toggle.
- **No hardcoded API key** ships in the repo. The key lives in the user prefs
  file, entered via the Preferences UI.

---

## Architecture

### Provider abstraction
Introduce an interface both providers implement so the model/selection layer is
untouched:

```java
public interface EpisodeDataProvider {
    void getShowOptions(ShowName showName) throws TVRenamerIOException, DiscontinuedApiException;
    void getSeriesListing(Series series) throws TVRenamerIOException;
}
```

- `TheTVDBLegacyProvider` — a **thin instance wrapper** whose two methods
  delegate to the existing `TheTVDBProvider` static methods. `TheTVDBProvider`
  itself is left **byte-for-byte unchanged** (smallest possible diff; v1 truly
  untouched).
- `TheTVDBv4Provider` — new (v4 JSON), described below.
- `TvdbProviders` — a small selector: `current()` reads the provider preference
  and returns the active `EpisodeDataProvider` singleton.

### Integration points (4 call sites)
Replace direct static calls with `TvdbProviders.current()`:
- `getShowOptions`: `ShowStore.java:468`, `PreferencesDialog.java:2291`, `PreferencesDialog.java:2320`
- `getSeriesListing`: `ListingsLookup.java:62`

**Key property:** both providers populate the *same* models —
`showName.addShowOption(id, name, firstAiredYear, aliasNames)` and
`series.addEpisodeInfos(EpisodeInfo[])` + `series.listingsSucceeded()`. So
`ShowSelectionEvaluator`, disambiguation, `ShowStore` caching, and `FileEpisode`
are entirely unaware of which provider answered. The Matching-tab override/
disambiguation validation (which calls `getShowOptions`) therefore also works
against whichever provider is selected.

---

## v4 provider internals (`TheTVDBv4Provider`)

Base URL: `https://api4.thetvdb.com/v4`

### Auth / token lifecycle
- `POST /login`, JSON body `{"apikey":"<key>"}`, headers `Accept`/`Content-Type:
  application/json`. Read the JWT at `data.token`.
- Token held **in memory** (per provider instance). All data calls send
  `Authorization: Bearer <token>` + `Accept: application/json`.
- **On `401`: re-login once and retry the request** (the retry both reference
  projects omit). A second `401` surfaces as an auth error.
- Missing/blank key → fail fast with a clear `TVRenamerIOException`
  ("TheTVDB v4 API key not configured") before any network call.

### Endpoints
| Purpose | Method + path | Maps to |
|---|---|---|
| Login | `POST /login` body `{"apikey":…}` | token |
| Search | `GET /search?query=<name>&type=series` | `showName.addShowOption(tvdb_id, name, year, aliases)` |
| Episodes | `GET /series/{id}/episodes/{season-type}?page=N` | `EpisodeInfo[]` → `series.addEpisodeInfos` |

- **Search** clears existing options first (parity with v1
  `showName.clearShowOptions()`), URL-encodes the query, and maps each result's
  `tvdb_id`, `name`, `year`, `aliases[]` into `addShowOption`. Year + aliases
  preserve the existing evaluator's scoring/disambiguation behaviour.
- **Episodes** paginates via `data.links.next` (more robust than the reference
  projects' hardcoded `<500` page-size heuristic). Per episode, read
  `seasonNumber`, `number`, `name`, `aired` → `EpisodeInfo`. Episodes missing
  season/number are skipped. Finish with `series.listingsSucceeded()`.

### Season-type (DVD ordering)
- `preferDvdOrderIfPresent == true` → request season-type `dvd`; if that returns
  `404`/empty (series has no DVD ordering), **fall back to `default`**.
- `preferDvdOrderIfPresent == false` → request `default`.
- v4 ordering is encoded by the endpoint, so the chosen ordering is written into
  the standard `EpisodeInfo` season/episode fields and the v4-sourced `Series`
  is marked `setPreferDvd(false)` (no per-episode dual-ordering as in v1).

### Language
English only. Default endpoints return default-language names; if forced English
is later needed, v4 offers `/series/{id}/episodes/{season-type}/{lang}` — out of
scope now.

### Verified live shape (2026-08-04, against a real key)
- **Search result** fields used: `tvdb_id` (clean numeric string, e.g. `"403245"`),
  `name`, `year` (string, e.g. `"2023"`), `aliases` (array of **strings**). Also
  present but unused: `id`/`objectID` (`"series-403245"`), `first_air_time`,
  `overview`, `status`, `country`, nested `remote_ids`.
- **Episodes** envelope: `data.series` (series meta) + `data.episodes[]`. Each
  episode: `id`, `seriesId`, `seasonNumber`, `number`, `name`, `aired`
  (`YYYY-MM-DD`), `runtime`; also `absoluteNumber` (available if absolute
  ordering is ever needed). Pagination: `data.links {next, total_items, page_size:500}`.
- **Gotcha:** `aliases` is a **string array in search** but an **object array
  `{language,name}` in the series payload** — parse each context separately.
- **Login:** token at `data.token` (~1.1 KB JWT).

### JSON parsing
Gson with small DTO classes for the three envelopes (`login`, `search`,
`episodes`). Only the `data` field is read on success; `status`/`message` are
read for error context. All parsing goes through an **injectable HTTP transport
seam** (an interface wrapping the actual `POST`/`GET`) so auth, retry, and
parsing are unit-testable without network.

---

## Preferences & UI

### New preferences (`UserPreferences` + `UserPreference` enum)
- `episodeDataProvider` — enum `TVDB_V1` | `TVDB_V4`, **default `TVDB_V1`**.
- `tvdbV4ApiKey` — String, default empty. Persisted in the user prefs XML like
  all other prefs.
- Both fire a preference-change event on setter (guarded by `valuesAreDifferent`).
- A new `EPISODE_DATA_PROVIDER` value is added to the `UserPreference` enum.
  When it fires, `ResultsTable.updateUserPreferences` re-drives currently-failed
  rows through the **existing** re-match engine added in Completed.md #57:
  `rematchRows(FileEpisode::isShowUnfound)`. Switching provider therefore
  retries the rows that failed under the previous provider, without disturbing
  already-matched rows.

### UI — new "TV data provider" group (General tab)
- Provider selector (radio or dropdown): **TheTVDB (v1)** / **TheTVDB (v4)**.
- API-key text field, enabled only when v4 is selected.
- **Validate** button: performs a live `POST /login` with the entered key and
  reports success/failure inline, reusing the Matching tab's existing async
  online-validation pattern (worker thread → `Display.asyncExec` back to UI).
- Saved via the existing `savePreferences()` burst.

---

## Error handling & diagnosability
- v4 errors map to the existing exception types: transport/`5xx`/parse →
  `TVRenamerIOException`; a series `404` on listings → treated as "no listings"
  the same way v1 failures are. The v1-specific `apiIsDeprecated`/`Discontinued`
  latch is NOT reused for v4.
- **Help page note (in scope):** add guidance to `src/main/resources/help/*.html`
  (and README where relevant): if a provider consistently reports "No info" for a
  show you know exists, the provider's search may be degraded — switch provider
  in Preferences (and, for v4, confirm the API key validates).

---

## Dependency change
- Add **Gson** to `gradle/libs.versions.toml` (version pinned there, per repo
  convention), wire into `build.gradle` dependencies, and run
  `./gradlew dependencies --write-locks` to update `gradle.lockfile`.
- Confirm Gson is bundled into the shadow/fat JAR (it must be on the runtime
  classpath in the EXE). Verify `shadowJar createExe` + launch.

---

## Testing
Unit tests (no network), using **fictional show names** per repo rules:
- **v4 search parsing:** captured sample search JSON → expected `ShowOption`s
  (id/name/year/aliases), including multi-result and empty-result cases.
- **v4 episode parsing:** captured episodes JSON (incl. a `links.next` second
  page) → expected `EpisodeInfo[]`; missing-field episodes skipped.
- **Auth/retry:** with a mocked transport seam — first call `401` → re-login →
  retry succeeds; second consecutive `401` → auth error; blank key → fail-fast.
- **Season-type selection:** pref on → `dvd` requested, `404` → `default`
  fallback; pref off → `default`.
- **Provider selector:** `episodeDataProvider` pref → correct `EpisodeDataProvider`.
- Existing v1 tests remain untouched and green.

---

## Verification (end-to-end)
1. `./gradlew build` (unit tests). Then `./gradlew shadowJar createExe`.
2. Manual (Windows):
   - Default (v1) unchanged: existing behaviour, no key needed.
   - Preferences → set provider **v4**, paste a valid key, **Validate** → success.
   - Load `Silo s01e08 Hanna.mkv` (and the other failing examples) → v4 resolves
     the show and episode where v1 returned "No info".
   - Invalid key → Validate reports failure; lookups fail cleanly, not silently.
   - Toggle back to v1 → behaviour reverts.
   - Confirm the DVD-order pref changes v4 episode numbering for a series that
     has DVD ordering.
3. Debug logging (`-Dtvrenamer.debug=true`): confirm v4 issues `/login` once,
   reuses the token, and searches by name.

---

## Risks / counterpoints
- **Permanent provider doubling:** two auth models + two parsers (XML + JSON) +
  a JSON dependency, maintained indefinitely. Accepted as the likely eventual
  direction anyway.
- **Manual toggle is invisible to non-experts:** a degraded provider shows
  "No info"; the remedy is a preference the user may not know exists. Mitigated
  by the help-page note rather than an in-app nudge.
- **v4 requires a user-registered key:** friction v1 never had; the shipped app
  must be keyless and direct users to obtain their own key.
- **v4 search is unproven in this codebase:** neither reference implements it, so
  result-shape assumptions (`tvdb_id`, `year`, `aliases`) must be validated
  against a live response during implementation.
