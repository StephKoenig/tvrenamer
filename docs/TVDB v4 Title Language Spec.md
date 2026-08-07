# TVDB v4 Title Language — Design Spec

## Context

TVRenamer renames files using the show name (`%S`) and episode title (`%t`)
returned by the provider. Under the v4 provider these currently come back in the
show's **primary language** — whatever the series was originally catalogued in.
For a show whose primary language is not the user's, the output is "wrong
language": e.g. a series catalogued in Spanish renames to its Spanish title even
when the user wants English.

Verified against the live v4 API (2026-08-04):

- **Matching is already language-agnostic.** Searching a series by either its
  original-language name or its English name returns the *same* series id. So
  this feature is about **output**, not matching — no search change is needed.
- **Series name is translatable** via `GET /series/{id}/translations/{lang}`
  (returns the translated `name`). The series object also exposes a
  `nameTranslations` array listing which language codes are available.
- **Episode titles are translatable** via
  `GET /series/{id}/episodes/{season-type}/{lang}` (returns per-episode `name`
  in the requested language, falling back per-episode to the default where a
  translation is absent).
- The language-specific *episodes* endpoint does **not** translate the series
  object embedded in its envelope — so the series name needs its own
  translations call.

**Goal:** let the user pick a **Title language** (default English) that governs
the language of the show name and episode titles written into renamed files
under the v4 provider.

## Non-goals / out of scope
- The v1 provider. It is fixed to English (`/all/en.xml`) and has no
  translation support; it stays exactly as-is. The Title-language setting is
  ignored (and its control disabled) when v1 is selected.
- Changing matching / search behaviour (already language-agnostic).
- Subtitle language (a separate, unrelated preference).
- Translating anything other than `%S` and `%t`.

## Decisions (settled with the user)
- The setting governs **both** the show name (`%S`) and episode titles (`%t`).
- **Default is English (`eng`)** — so English-primary shows behave exactly as
  today; the feature is a no-op unless the user opts into another language.
- Graceful fallback to the show's primary/default language when a translation
  is unavailable — never a blank title.
- v4-only; v1 untouched.

## Language list

A new enum `TitleLanguage` (display name → ISO 639-2/T code), English first/default:

| Display | Code | | Display | Code |
|---|---|---|---|---|
| English | `eng` | | Japanese | `jpn` |
| Spanish | `spa` | | Korean | `kor` |
| French | `fra` | | Chinese | `zho` |
| German | `deu` | | Arabic | `ara` |
| Italian | `ita` | | Swedish | `swe` |
| Portuguese | `por` | | Polish | `pol` |
| Dutch | `nld` | | Turkish | `tur` |
| Russian | `rus` | | | |

Modelled on `ThemeMode`/`EpisodeDataProviderType`: `toString()` returns the
display name (for the UI combo and persistence-by-name), a `code()` accessor
returns the 3-letter API code, and `fromString(...)` is case-insensitive.

## Architecture

### Preference
- New `titleLanguage` preference on `UserPreferences` (enum `TitleLanguage`,
  default `ENGLISH`), persisted exactly like `themeMode` — register in the
  constructor default, `fromParsedXml`, `UserPreferencesPersistence.persist()`
  + `SCALAR_FIELDS`, and add a `TITLE_LANGUAGE` value to the `UserPreference`
  enum. Setter guards with `valuesAreDifferent` + `preferenceChanged`.

### Episode titles (the clean path)
The title flows through a mutable pipeline set entirely in the parser
(`EpisodeInfo.episodeName` → `Episode.title` → `%t`), so only the request URL
changes:
- Thread the language code from `TheTVDBv4Provider.getSeriesListing` →
  `fetchAll(...)` → `TvdbV4Client.episodesJson(id, seasonType, lang, page)`,
  building `/series/{id}/episodes/{seasonType}/{lang}?page=N`.
- Wrap the language request in the **same try/fallback pattern already used for
  the DVD→default season-type fallback**: if the language-qualified request
  throws or yields no episodes, retry the un-qualified
  `/series/{id}/episodes/{seasonType}` (default language). `V4Parser.parseEpisodes`
  is unchanged — it already reads `name`.
- Interaction with the existing DVD-order fallback: season-type is chosen first
  (`dvd` vs `default`, with its existing empty→`default` fallback), then the
  language segment is appended to whichever season-type is used; a failed
  language request falls back to that same season-type without a language
  segment.

### Series name (needs a display-name override)
`ShowOption.name` is `final` and the `Series` is created at *search* time from
the primary-language name, so the translated name cannot be injected there
cheaply (search returns candidates; translating each would be N extra calls and
would also pollute the disambiguation UI with translated names). Instead:
- Add `TvdbV4Client.seriesTranslationName(id, lang)` →
  `GET /series/{id}/translations/{lang}`, returning the translated `name` (or
  null if unavailable / call fails).
- Add a **mutable display-name override** to `Show` (or `ShowOption`): a
  nullable `displayNameOverride` field with a setter; `getName()` returns the
  override when set, otherwise the original `name`. This is the single read
  point for `%S` (`EpisodeReplacementFormatter` reads `show.getName()`), so no
  formatter change is needed.
- In `TheTVDBv4Provider.getSeriesListing` (where the resolved series id is
  available): **reset** the override first (so a prior provider/language choice
  can't leak via the cached `Series` in `KNOWN_SERIES`), then, if the title
  language is not the show's already-default, fetch the translation and set the
  override when a non-blank translated name is returned. If the language is
  unavailable, leave the override cleared → original name is used.
- The reset-then-maybe-set discipline makes the override deterministic on every
  listings fetch, which also addresses the cross-provider `KNOWN_SERIES` cache
  note deferred from the previous review (a v4-set name can no longer persist
  onto a later v1 lookup of the same series).

### UI
- Add a **Title language** dropdown to the existing "TV data provider" group in
  Preferences → General, populated from `TitleLanguage.values()`.
- Enabled only when the v4 provider is selected (mirror the existing
  enable/disable of the API-key field via `updateProviderControlsEnabled()`);
  disabled with the v1 provider since v1 is English-only.
- Load the current value on open; read back + commit in `savePreferences()`
  (mirror the `themeMode`/provider combo round-trip).

### Re-match on change (decided: applies to subsequently-matched files)
Changing the title language affects which language titles are fetched **on the
next match**. It does **not** retroactively re-fetch already-resolved rows in the
current session. Rationale: episode listings are cached per `Series`
(`listingsStatus == SUCCESS` returns immediately), so a live refresh of resolved
rows would require invalidating both the `Series` listings cache and the
display-name override for affected rows and re-issuing language-qualified
fetches — materially more complex and risk-prone than the feature warrants.
`TITLE_LANGUAGE` therefore does **not** get a re-match case in
`ResultsTable.updateUserPreferences` (it falls into the no-op group); the new
language takes effect for files added or re-matched after the change (including
via a provider switch, which already re-drives rows).

Live retroactive refresh (invalidate the affected `Series` listings +
display-name override on a `TITLE_LANGUAGE` change, then re-fetch) is a possible
future enhancement, explicitly out of scope here. **Flag this at the user-review
gate** — if the user wants language changes to update already-loaded rows
immediately, the scope grows to include that cache-invalidation work.

## Insertion points (from code mapping)
- `%S` read: `EpisodeReplacementFormatter` `show.getName()`; stored value
  `ShowOption.name` (final) + new `displayNameOverride`.
- `%t` read: `EpisodeReplacementFormatter` `episode.getTitle()`; value
  `Episode.title` ← `EpisodeInfo.episodeName` ← `V4Parser.parseEpisodes` `name`.
- v4 HTTP: `TvdbV4Client.episodesJson` (add lang segment) + new
  `seriesTranslationName`.
- Prefs: `UserPreferences`, `UserPreferencesPersistence`, `UserPreference`,
  `PreferencesDialog` (mirror `themeMode` + the provider group).

## Fallback semantics (summary)
1. Title language = show's primary language (or English on an English-primary
   show) → behaves exactly as today.
2. Series translation missing for the chosen language → original series name.
3. Episode translation missing → TVDB returns the default per-episode name.
4. Language-qualified episodes request errors → retry without the language
   segment (same season-type).
5. Never produce an empty `%S`/`%t`.

## Testing
Unit tests (no network; fictional show/episode names, e.g. a series with a
Spanish primary name and an English translation, both invented):
- `TitleLanguage.fromString` / `code()` round-trip.
- `titleLanguage` preference persistence round-trip + default English.
- `TvdbV4Client` URL construction: episodes URL includes `/{lang}`; translation
  URL is `/series/{id}/translations/{lang}`.
- v4 provider: display-name override set from a stubbed translation; override
  reset when translation absent; episode-language fallback to the un-qualified
  endpoint on error/empty.
- `getName()` returns the override when set, the original name otherwise.
- Existing v4/v1 tests remain green.

## Verification (end-to-end, Windows)
1. `./gradlew build`; then `shadowJar createExe`.
2. Preferences → General → v4 selected → set Title language = English; add a
   file for a non-English-primary show → confirm `%S` and `%t` render in
   English. Switch Title language to the show's primary language → confirm the
   original-language title. Switch to a language the show lacks → confirm
   graceful fallback to the primary-language name (no blank).
3. Confirm the dropdown is disabled under the v1 provider.
4. `-Dtvrenamer.debug=true`: confirm the episodes request carries the language
   segment and a single translations call per resolved series.

## Risks / counterpoints
- **Extra API call per resolved series** (the translations fetch). Bounded (one
  per series, cached by `Series`); acceptable, and skipped when the language
  equals the default.
- **New mutable field on an otherwise-immutable `ShowOption`/`Show`.** Kept
  minimal (one nullable override + setter) and made safe by the reset-on-every-
  listings-fetch discipline; still a small departure from the current immutable
  design.
- **Episode-level translations are sparser than series-level** on TVDB. Many
  shows have a translated series name but only default-language episode titles;
  the per-episode fallback means the user may see a translated show name with
  original-language episode titles. This is TVDB data completeness, not a bug —
  worth noting in the help text.
- **v1 asymmetry**: the setting silently does nothing under v1. Disabling the
  control for v1 communicates this, but a user switching providers may be
  surprised; the help page should state the setting is v4-only.
