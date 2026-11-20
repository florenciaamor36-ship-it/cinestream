# CineStream

CineStream opens directly to a Netflix-style browsing catalog—no sign-in, Telegram account, or VPS is required. Its bundled Internet Archive feature-films snapshot contains nearly all 29,634 search hits (some records tagged as explicit adult content are intentionally excluded).

The app keeps source titles and descriptions and offers an on-demand Spanish translation of an item's title and description using downloadable on-device ML Kit models; the original record stays available. Automatic translation can be imperfect. This translates catalog text only, never audio or subtitles. A language field means only that the Archive metadata lists it, not that the audio/subtitles have been checked.

The source query returns mixed kinds of records and metadata quality varies. Archive's `year` field can be the record/upload year, not theatrical release year, and posters/metadata may be missing. The app presents it as a year indicated by the source.

CineStream only starts in-app video playback when the item currently declares Creative Commons Public Domain Mark 1.0, and then resolves an MP4 from its current metadata. This declaration is not independent legal verification. Items with other or no declared license remain in the catalog and link to the original record, but are not playable in-app. Individual availability/compatibility still needs testing.

`app/src/main/assets/ia_catalog_v2.dat` holds the source snapshot. `tools/build_ia_catalog.py` uses the official Internet Archive cursor-based scraping API; `tools/validate_ia_catalog.py` validates the bundled catalog.

## Build

Build/test with `gradle :app:assembleDebug :app:testDebugUnitTest`. GitHub Actions also uploads the debug APK as an artifact for 14 days.
