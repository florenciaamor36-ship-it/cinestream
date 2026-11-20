# CineStream

CineStream is an Android app for browsing film records and playing compatible files directly from their source. The first no-login/no-server catalog is a bundled snapshot of 2,300 Internet Archive movie records whose metadata declares Creative Commons Public Domain Mark 1.0. The catalogue does not claim independent legal verification; check each original item before reusing or redistributing media.

## First catalog milestone

- The Android app opens directly to a Netflix-style catalog; no Telegram login, bot, account, or VPS is required for browsing.
- Search and browse locally bundled titles with poster thumbnails, year/duration when supplied, subjects, language and creator where supplied, synopsis/description, source page, and declared-rights warning.
- Ratings, language/subtitle availability and resolution are not fabricated. Missing metadata is left blank.
- On Play, the app rechecks the record's declared license and chooses a listed MP4 file from Internet Archive metadata, then plays the source URL directly. Availability, range seeking, playback compatibility, and rights claims still require per-title/device verification.
- `app/src/main/assets/ia_public_domain_candidates.json` contains the 2,300-record snapshot and query provenance.

The snapshot comes from Internet Archive Advanced Search (`mediatype:movies AND collection:feature_films AND licenseurl:"http://creativecommons.org/publicdomain/mark/1.0/"`). That mark is a metadata declaration, not a guarantee that every item is legally in the public domain. The app links to the original record and displays this caveat.

## Build

Open the project in Android Studio and build `:app:assembleDebug`. CI builds and tests the Android project on pushes and pull requests.
