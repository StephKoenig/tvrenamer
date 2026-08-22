A new **keyless** metadata provider and the removal of the retired TheTVDB v1 API.

## TVMaze: a keyless data provider, now the default

TheTVDB's legacy v1 API has been deprecated and no longer returns search results. This release adds **[TVMaze](https://www.tvmaze.com/)** as an episode-data provider and makes it the **default** — so lookups work out of the box with **no API key and no setup**.

- **TVMaze (default)** — keyless. Nothing to configure; it just works.
- **TheTVDB (v4)** — still available as an alternative. Requires a personal API key (free, from thetvdb.com), and supports the selectable **Title language** for output show names and episode titles.
- Choose the provider in **Preferences → General → TV data provider**.
- **Switching providers** re-runs the lookup for any rows that hadn't matched, and clears the internal caches so the two providers (which use different id systems) never collide.

## TheTVDB v1 removed

The deprecated v1 provider has been removed entirely. If you were still on v1 (or a fresh install defaulted to it), TVRenamer now uses **TVMaze** automatically. Your TheTVDB **v4** setting and API key, if configured, are unchanged.

## Notes

- TVMaze is English-centric and has a single episode ordering, so the **Title language** and **Prefer DVD episode order** settings apply only to the TheTVDB v4 provider.
- TVMaze is rate-limited; very large batches may pause briefly (the client automatically retries once when throttled).
- The in-app Help pages and README were updated to describe lookups in terms of the selectable provider.
