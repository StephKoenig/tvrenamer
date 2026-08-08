A focused release that lets you choose the **language** of the show name and episode titles in your renamed files, when using the TheTVDB v4 provider.

## Choose the output language for titles

Some shows are catalogued in a language other than English — so under the v4 provider they would rename to their original-language title even when you wanted English (or vice-versa). You can now pick the output language in **Preferences → General → Title language**.

- **Default is English.** Fifteen languages are available (English, Spanish, French, German, Italian, Portuguese, Dutch, Russian, Japanese, Korean, Chinese, Arabic, Swedish, Polish, Turkish).
- Both the **show name** and the **episode titles** are pulled in the language you choose — including the destination **sub-folder** name, so the folder, the filename, and any embedded metadata tag all agree.
- **Graceful fallback:** if a show doesn't have a translation in your chosen language, that title falls back to the show's original language rather than coming out blank. (Some shows have a translated show name but only original-language episode titles — that's down to TheTVDB's data.)
- The setting is **only used with the TheTVDB v4 provider** (it's disabled when the v1 provider is selected, since v1 is English-only) and requires the v4 API key you set up previously.

Notes:

- **Matching is unaffected.** Lookups already work regardless of the filename's language, so a file named in either language still finds the right show — this setting only governs the *output* titles.
- Changing the Title language applies to files matched **after** the change; files already loaded keep their current titles until they're re-added.
