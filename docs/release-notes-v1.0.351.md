A resilience-focused release. The headline is a **selectable episode-data provider**: when TheTVDB's legacy search stops returning results, you can switch to its modern API and keep working — without waiting for an upstream fix. This release also makes the Matching preferences take effect immediately.

## Choose your TheTVDB API: v1 or v4

TVRenamer has always looked shows up through TheTVDB's legacy **v1** XML API. That API's *name search* can silently start returning empty results for every show — even while the rest of it still works — which makes every file come back as "No info" for reasons entirely outside the app.

You can now pick the provider in **Preferences → General → TV data provider**:

- **TheTVDB (v1)** — the long-standing default. No API key, nothing to configure. Still the right choice when it's working.
- **TheTVDB (v4)** — the modern JSON API. Requires your own personal API key (create one at thetvdb.com). Paste it into the key field and click **Validate**: the field tints **green** when the key works and **red** when it doesn't, with a status message beside it.

Details:

- **Switching the provider re-runs the lookup for rows that hadn't matched**, so flipping to v4 immediately retries the files that v1 couldn't resolve — no need to remove and re-add them.
- **The "Prefer DVD episode order" preference is honoured under v4** as well as v1.
- Your choice and key are saved with your other preferences; v1 remains the default for anyone who doesn't opt in, so nothing changes unless you want it to.
- The **Troubleshooting** help page now explains what to do when a show you know exists keeps coming back as "No info": switch the provider (and, for v4, confirm the key validates).

## Matching preferences apply immediately

Changing a **show-name override** or a **disambiguation** in **Preferences → Matching** and clicking Save now **re-matches the affected or still-unmatched rows automatically**. Previously those changes only took effect for files added afterwards, so you had to remove and re-add existing files to pick up a corrected match. Rows whose result can't change are left untouched, so only the affected shows are re-queried. (Renaming-tab changes such as the filename format and season prefix already updated the table live.)

## Notes

- The v4 provider is entirely opt-in. If you never open the new setting, TVRenamer behaves exactly as before.
- v4 support is new; if you hit an edge case, switching back to v1 is a single dropdown change.
