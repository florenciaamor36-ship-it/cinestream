# CineStream

CineStream opens directly to a local snapshot of full-movie candidates from curated YouTube playlists; no login, registration, VPS, paid API key, or subscription is required. The catalog filters out trailers and clips and keeps the source/channel information.

## In-app YouTube playback

Playback uses the GPL-licensed NewPipe Extractor engine already used by the user's `la-clave-videos` project, together with CineStream's Media3 player. It resolves a video-only stream and requires a separately identified Latin-American Spanish audio track (`es-419` or a supported Latin-American locale) before starting playback. If YouTube does not expose an explicit qualifying audio track or stream, CineStream reports that instead of presenting unverified audio as Latin American Spanish. Streams play within CineStream; the app does not open an external player.

YouTube may change or restrict its public endpoints, and availability can vary by video, region, age, network, and device. A title in the bundled snapshot is a candidate, not a guarantee that it has a qualifying Latin-American audio track or will remain available.

CineStream is distributed under GPL-3.0. See `LICENSE` and `THIRD_PARTY_NOTICES.md` for license and attribution details. The full corresponding source is this repository.

The curated catalog contains a bundled snapshot so titles can be listed offline; refreshing uses the playlist RSS feeds. Playback requires an Internet connection.

`tools/validate_youtube_catalog.py` checks the catalog structure. CI builds the app and runs unit tests plus an Android emulator playback smoke test. A build or passing unit tests alone does not establish that playback works; the smoke test must report `PLAYING`.

## Build

Run `python3 tools/validate_youtube_catalog.py`, `gradle :app:assembleDebug :app:testDebugUnitTest`, or use the repository's GitHub Actions workflow.
