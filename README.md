# CineStream

CineStream opens directly to a catalog of full-length YouTube movie candidates—no login, registration, VPS, paid API key, or subscription is required. A bundled snapshot appears immediately; refreshing combines curated playlist feeds with NewPipe searches across additional YouTube channels. The app filters out trailers, clips, shorts, and short videos, and groups results into genres from their published titles and descriptions.

## Playback and language

Playback uses the GPL-3.0 NewPipe Extractor engine already used by the user's `la-clave-videos` project, with CineStream's Media3 player. The resolver prefers explicitly identified Latin-American Spanish audio, but accepts any track identified as Spanish, including Spain Spanish and generic Spanish. It does not mislabel an untagged track as Spanish. If YouTube provides no identified Spanish audio track for a video, CineStream explains why it cannot play that title; it does not open an external app.

YouTube's public search and extraction endpoints can change or restrict access, and video availability varies by region, age, network, and channel. A catalog result is a candidate, not a guarantee that playback remains available. Category labels are inferred from title/description and may be imperfect; missing runtime, release year, and rating are not invented.

CineStream is distributed under GPL-3.0. See `LICENSE` and `THIRD_PARTY_NOTICES.md` for license and attribution details. No paid API key or subscription is required. The complete corresponding source is this repository.

The bundled title snapshot can be browsed offline; refreshing uses YouTube feeds/search, and video playback needs the Internet.

`tools/validate_youtube_catalog.py` checks the catalog structure. CI builds the app and runs unit tests plus an Android emulator playback smoke test. A build or passing unit tests alone does not establish that playback works; the smoke test must report `PLAYING`.

## Build

Run `python3 tools/validate_youtube_catalog.py`, `gradle :app:assembleDebug :app:testDebugUnitTest`, or use the repository's GitHub Actions workflow.
