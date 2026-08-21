# TVMaze Provider — Design Spec

## Context

TVRenamer looks up shows/episodes through a pluggable `EpisodeDataProvider`
(introduced for the TheTVDB v4 work). Two implementations exist today:
`TheTVDBLegacyProvider` (v1 XML) and `TheTVDBv4Provider` (v4 JSON), selected by
the `episodeDataProvider` preference (`EpisodeDataProviderType.TVDB_V1` /
`TVDB_V4`), default `TVDB_V1`.

**TheTVDB v1 is deprecated and its name-search returns empty for every query**,
so the current default provider is non-functional — new installs get broken
lookups until the user manually switches. This spec replaces v1 with **TVMaze**
(`https://api.tvmaze.com`), a free, **keyless**, JSON REST provider, and makes it
the new default.

Verified live against the TVMaze API (2026-08-08):

- **Search:** `GET /search/shows?q=<query>` → JSON array of
  `{ "score": <float>, "show": { "id", "name", "premiered" (YYYY-MM-DD),
  "language", … } }`, ranked by score. No key. HTTP 200.
- **Episodes:** `GET /shows/{id}/episodes` → JSON array of
  `{ "id", "season", "number", "name", "airdate" (YYYY-MM-DD), … }`.
- **Show (optional):** `GET /shows/{id}` → `{ "id", "name", "premiered", … }`.
- No authentication. IP rate limit (~20 calls / 10s) → occasional HTTP 429.

## Goal

Add a `TVMaze` provider, make it the keyless default, and remove the dead v1
provider entirely.

## Non-goals / out of scope
- The TheTVDB **v4** provider stays exactly as-is (key, Validate, Title language).
- Subtitle language; the Title-language and Prefer-DVD-order settings do not
  apply to TVMaze (no per-episode translations, single episode ordering) — they
  are simply ignored for TVMaze, and the v4-only controls stay disabled as they
  already are for non-v4 providers.
- Using TVMaze's extra endpoints (AKAs, cast, images).

## Decisions (settled with the user)
- **Replace v1 entirely:** delete `TheTVDBLegacyProvider`, the v1 `TheTVDBProvider`,
  and v1-only tests/constants. v1 is deprecated and non-functional.
- **TVMaze is the new default** provider (keyless — works out of the box).
- Cache isolation on provider switch is **required** (see Architecture) because
  TVMaze and TheTVDB use different id namespaces.

## Architecture

### Provider implementation (mirrors the v4 structure)
New package `org.tvrenamer.controller.tvmaze`:
- `TvMazeClient` — keyless HTTP via the existing `java.net.http` transport seam
  pattern; methods `searchShowsJson(query)` → raw JSON, `episodesJson(id)` → raw
  JSON. On HTTP 429, retry once after a short delay; non-200 (other than the
  retried 429) → `TVRenamerIOException`.
- `TvMazeParser` — pure JSON→model:
  - `parseSearchShows(json)` → `List<TvMazeResult(id, name, year, aliases)>`,
    where `id = show.id` (as String), `name = show.name`, `year` = first 4 chars
    of `show.premiered` (null if absent), `aliases` = empty list (TVMaze search
    provides none). Skips malformed entries; catches `JsonSyntaxException` →
    empty list.
  - `parseEpisodes(json)` → `List<EpisodeInfo>` built from
    `id→episodeId, season→seasonNumber, number→episodeNumber, name→episodeName,
    airdate→firstAired`; skips entries missing season/number; catches
    `JsonSyntaxException` → empty list.
- `TvMazeProvider implements EpisodeDataProvider`:
  - `getShowOptions(showName)`: `showName.clearShowOptions()`, then for each
    parsed result `showName.addShowOption(id, name, year, aliases)`.
  - `getSeriesListing(series)`: fetch `episodesJson(series.getId())`, parse,
    `series.setPreferDvd(false)`, `addEpisodeInfos(...)`, `listingsSucceeded()`.
    (No language segment, no DVD variant, no translations call.)

### Enum, selector, default
- `EpisodeDataProviderType`: **remove `TVDB_V1`, add `TVMAZE("TVMaze")`** (the
  enum takes only a display label — no `code()`, unlike `TitleLanguage`).
  `TVDB_V4` unchanged. Keep the existing `fromString(...)` case-insensitive match.
- `TvdbProviders.current()`: `TVMAZE → TvMazeProvider`, `TVDB_V4 →
  TheTVDBv4Provider`. Remove the v1 branch and the `TheTVDBLegacyProvider`
  singleton.
- `UserPreferences` default `episodeDataProvider = TVMAZE`.

### Preference migration
`EpisodeDataProviderType.fromString(...)` returns null for the now-unknown
`"TVDB_V1"`; `fromParsedXml`/`setEpisodeDataProvider` already fall back to the
default when null. Changing the default to `TVMAZE` means: existing prefs holding
`TVDB_V1` (dead) auto-migrate to TVMaze on next load; `TVDB_V4` users keep v4 and
their key. No explicit migration code needed beyond the default change.

### Cache isolation on provider switch (required)
TVMaze ids and TheTVDB ids are different namespaces. The shared caches key by
numeric id / query string:
- `Series.KNOWN_SERIES` (id → Series). `Series.createSeries(id, name)` **throws**
  if the same id is created with a different name — so a numeric-id collision
  across providers is a hard crash, not just a stale read.
- `ShowName` query cache (query → matched/failed show).

On an `EpisodeDataProviderType` change, **clear both caches** before re-matching.
Add `Series.clearKnownSeries()` and a `ShowName.clearAllQueryCache()` (names
indicative), and call them from the `EPISODE_DATA_PROVIDER` handling in
`ResultsTable.updateUserPreferences` *before* the existing
`rematchRows(FileEpisode::isShowUnfound)`. This prevents cross-provider id
collisions/crashes and stale matches; the cost is that switching providers
re-fetches, which is correct.

### v1 removal
Delete `controller/TheTVDBLegacyProvider.java` and `controller/TheTVDBProvider.java`
(v1 XML). Remove v1-only tests (e.g. `TheTVDBProviderTest`) and any test/integration
references. Remove now-unused v1 constants (`TVDB_API_KEY`, and `DEFAULT_LANGUAGE`
if it becomes unused — verify with grep before deleting). Confirm no remaining
references (XmlUtilities/XPathUtilities may be shared with other code — only
remove what is exclusively v1).

### UI
- The provider dropdown lists `TVMaze` and `TheTVDB (v4)` (v1 gone).
- `updateProviderControlsEnabled()` already enables the API-key/Validate/Title-
  language controls only for v4; TVMaze (not v4) leaves them disabled — no change
  needed beyond the enum swap.

## Error handling
- 429 → one retry after a short delay, then `TVRenamerIOException`.
- Malformed JSON → parser returns empty (graceful; listing/search degrades to
  no results rather than crashing) — same hardening applied to the v4 parser.
- Network/other non-200 → `TVRenamerIOException`, surfaced as a failed row.

## Testing
Unit tests (no network; fictional show/episode names):
- `TvMazeParser.parseSearchShows`: maps id/name/year(from premiered)/empty
  aliases; skips malformed; empty on `JsonSyntaxException`.
- `TvMazeParser.parseEpisodes`: maps fields; skips missing season/number; empty
  on `JsonSyntaxException`.
- `TvMazeClient`: search/episodes URL construction via the transport fake; 429 →
  retry-once then success; second 429/non-200 → throws.
- `TvMazeProvider`: getShowOptions populates ShowName; getSeriesListing populates
  Series + setPreferDvd(false).
- `EpisodeDataProviderType`: `TVMAZE` present, `TVDB_V1` gone; `fromString("TVDB_V1")`
  → null (→ default TVMaze); default is TVMAZE.
- `TvdbProviders.current()`: TVMAZE→TvMazeProvider, TVDB_V4→TheTVDBv4Provider.
- Cache clear: after `clearKnownSeries()`, a previously-cached id can be
  re-created with a different name without throwing.
- Existing v4 tests stay green; v1 tests removed.

## Verification (end-to-end, Windows)
1. `./gradlew clean build shadowJar createExe`.
2. Fresh install (or prefs with old `TVDB_V1`) → provider defaults to **TVMaze**,
   no key required; add files → they resolve via TVMaze.
3. Switch provider TVMaze ↔ TheTVDB v4 → no crash, rows re-fetch under the new
   provider (cache isolation works).
4. Confirm the API-key/Validate/Title-language controls are disabled under TVMaze.
5. `-Dtvrenamer.debug=true`: TVMaze search + episodes calls, no auth header.

## Risks / counterpoints
- **Deleting v1** is irreversible in-tree, but v1's search is dead and v4/TVMaze
  cover lookups; keeping dead code has no value.
- **Clearing caches on switch** makes provider switching re-fetch everything
  (slower switch) — the correct tradeoff to avoid the id-collision crash.
- **TVMaze rate limits** (~20/10s). Bounded by the 4-thread pool; the 429 retry
  handles transient limits, but very large batches could still be throttled —
  acceptable, and no worse than a metadata lookup should be.
- **TVMaze is English-centric**: no Title-language benefit (that stays a v4
  feature). Fine — TVMaze is the keyless "just works" default; v4 remains the
  option for translated titles.
- **Id-namespace mixing in persisted disambiguation overrides**: a TVMaze id
  stored under one provider and used under the other simply won't match
  candidates → graceful fallback to normal selection. Acceptable.
