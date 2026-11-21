# CineStream

CineStream opens straight to a browsable catalog of full movies from curated YouTube playlists. It reads YouTube's public playlist RSS feeds, filters out trailers/clips and unrelated uploads, and only accepts entries published by the expected channel for each playlist. Playback uses the official YouTube embedded player inside the app—CineStream does not scrape direct media URLs, download, or rehost the video.

Current sources span Film&Clips (Spanish and 4K playlists), Vision Video, Lifted, Deep C Digital, and Mosfilm (Spanish-subtitled films). The public RSS feeds expose a limited recent subset, so the catalog is not a search over all YouTube. It refreshes when stale and caches results in memory for up to six hours.

A public RSS entry and a successful embed page do not guarantee playback: a publisher can remove a film, restrict it by country/age, or disable embedding. YouTube decides availability. CineStream doesn't invent runtimes, release years, ratings, audio languages, or subtitle claims. The title and language details shown are what the channel publishes; Mosfilm's selected playlist advertises Spanish subtitles.

`tools/validate_youtube_catalog.py` checks that the configured curated playlist IDs and publisher filters are present and that the old noisy Archive snapshot is gone.

## Build

Run `python3 tools/validate_youtube_catalog.py`, `gradle :app:assembleDebug :app:testDebugUnitTest`, or use GitHub Actions to build and download the debug APK.
