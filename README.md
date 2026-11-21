# CineStream

CineStream opens directly to a catalog of full movies selected from curated YouTube playlists. The app now bundles a small snapshot of 62 current playlist entries that matched the full-movie title and publisher filters, so the titles are visible even when YouTube's feed cannot be resolved; a successful refresh replaces the snapshot with current entries. The player is embedded with a valid YouTube page origin/referrer and uses the official IFrame Player API so it can report a real PLAYING callback. It filters out trailers/clips and accepts items only when the RSS author matches the expected publisher.

Playback uses YouTube's official embedded player at `www.youtube-nocookie.com`; CineStream does not scrape direct media URLs, download, or rehost the video. YouTube playback still requires Internet access and may fail for region, age, publisher, or DNS/network restrictions. A bundled title list is not an offline video download. When the embed cannot load, the player shows a retry message and a button to open the YouTube watch page.

Current sources span Film&Clips (Spanish and 4K playlists), Vision Video, Lifted, Deep C Digital, and Mosfilm (a playlist advertised with Spanish subtitles). YouTube's public RSS feeds expose only recent playlist entries, so this is not a search across all YouTube. The app does not invent runtimes, release years, ratings, audio languages, or subtitles.

`tools/validate_youtube_catalog.py` validates the curated playlists, fallback list, video IDs and player domains. CI builds the app and runs unit tests. A separate Android WebView instrumentation smoke test checks the embedded player page on an emulator.

## Build

Run `python3 tools/validate_youtube_catalog.py`, `gradle :app:assembleDebug :app:testDebugUnitTest`, or use GitHub Actions to build the debug APK.
