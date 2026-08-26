A redesigned General preferences tab, a tidier Matching tab, and a full refresh of the project's dependencies and build tooling.

## Preferences: General tab redesign

The General tab had grown quite tall as features were added, with every option on its own full-width row. It has been reorganised to be easier to scan and shorter overall:

- **Grouped sections.** Options are now grouped under clear headings: **General**, **File Handling**, **Metadata and Subtitles**, **Data Provider**, and **Application**.
- **Two-column layout.** Related short options sit side by side, and the right-hand checkboxes (Rename Enabled, Remove emptied directories, Delete subtitle files, Prefer DVD order, Season Prefix Leading Zero) line up in a single column.
- **One-line data provider row.** The data provider, the TheTVDB v4 API key field, and the **Validate** button now share a single line, each sized to its content instead of stretching across the whole dialog.
- **Aligned dropdowns and fields.** The provider, Title language, Default subtitle language, and Theme dropdowns now share the same left edge, with their labels measured to fit exactly (no clipping, no excess whitespace).
- **No more `[?]` markers.** The `[?]` help markers have been removed from every option label. Help is still available by hovering the mouse over any option to see its tooltip; the old "Hover mouse over [?] to get help" hint has been removed.

## Preferences: Matching tab and overall height

- Removed the large empty gap between the **Overrides** and **Disambiguations** sections.
- Validation/help message lines no longer reserve blank space when empty; they appear only when there is something to show.
- Together with the removed hint row, the Preferences dialog is now noticeably shorter on all three tabs (General, Renaming, Matching).

## Under the hood: dependency and build-tooling refresh

All tracked dependencies were brought up to their latest releases:

- **gson** 2.11.0 → 2.14.0
- **JUnit Jupiter** 6.1.1 → 6.1.3
- **Shadow** (fat-JAR plugin) 9.4.2 → 9.6.1
- **Gradle** 9.6.1 → 9.7.1
- **SpotBugs** plugin 6.5.8 → 6.5.11

SWT (3.134.0) and Launch4j (4.0.0) were already current. Each build-tooling change was verified with a clean packaging build and confirmed on CI, with the packaged Windows executable's SWT manifest attributes checked intact.

## Bug fixes

- **Help:** Corrected the Preferences help page, which previously described a "Preserve Modification Time" option that did not match the actual "Set file modification time to now after move/rename" checkbox. The help now accurately describes the checkbox and its default (which preserves the original timestamp).

## Notes

- These changes are cosmetic and maintenance-focused: no preference behaviour changed, and existing settings are unaffected.
