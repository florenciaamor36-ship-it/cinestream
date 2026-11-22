from pathlib import Path
import json
import re

root = Path(__file__).resolve().parents[1]
source = (root / "app/src/main/java/com/example/data/repository/YouTubeMoviesCatalog.kt").read_text(encoding="utf-8")
rows = re.findall(r'PlaylistSource\("[^"\n]+", "([A-Za-z0-9_-]{10,})", "([^"]+)"\)', source)
playlist_ids = [row[0] for row in rows]
publishers = {row[1].casefold() for row in rows}
assert len(playlist_ids) >= 15, f"Expected at least 15 curated YouTube playlists; found {len(playlist_ids)}"
assert len(playlist_ids) == len(set(playlist_ids)), "Playlist IDs must be unique"
assert len(publishers) >= 5, f"Expected at least five publishers; found {len(publishers)}"
assert "youtube.com/feeds/videos.xml" in source
assert "https://www.youtube-nocookie.com/embed/" in source  # retained only as the seed's video-ID locator
resolver = (root / "app/src/main/java/com/example/data/youtube/YouTubeStreamResolver.kt").read_text(encoding="utf-8")
player = (root / "app/src/main/java/com/example/ui/screens/PlayerScreen.kt").read_text(encoding="utf-8")
assert "StreamInfo.getInfo" in resolver and "isLatinAmericanSpanish" in resolver
assert "YouTubeStreamResolver.resolve" in player and "MergingMediaSource" in player
assert "youtube.com/iframe_api" not in player
assert "IOException" in source and "loadBundled()" in source
assert "publisherMarker" in source and "fullMoviePattern" in source
assert "loadBundled().forEach" in source, "Online refresh must preserve the full bundled catalog"
manifest = (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
assert 'android.permission.INTERNET' in manifest, "Android needs INTERNET permission for YouTube playback"

seed_path = root / "app/src/main/assets/youtube_movies_seed.json"
seed = json.loads(seed_path.read_text(encoding="utf-8"))
items = seed["items"]
assert len(items) >= 1700, f"Expected at least 1700 offline fallback movies; found {len(items)}"
video_ids = [item["streamUrl"].split("/embed/", 1)[-1].split("?", 1)[0] for item in items]
assert len(video_ids) == len(set(video_ids)), "Offline seed contains duplicate video IDs"
assert all(re.fullmatch(r"[A-Za-z0-9_-]{11}", video_id) for video_id in video_ids)
assert all(item["streamUrl"].startswith("https://www.youtube-nocookie.com/embed/") for item in items)
assert all(item.get("genre") for item in items), "Every fallback movie needs a category"
assert any("Movie Central" in item.get("creator", "") for item in items), "Movie Central must appear in the fallback catalog"
assert any("FilmIsNow" in item.get("creator", "") for item in items), "FilmIsNow must appear in the fallback catalog"
assert all(item["sourceUrl"].startswith("https://youtube.com/watch?v=") for item in items)
assert all(item["rating"] == -1.0 and item["releaseYear"] == 0 for item in items), "Don't invent ratings or years"
assert all(item["durationMin"] >= 0 for item in items), "Runtime must be non-negative"
candidate_items = [item for item in items if item.get("youtubeChannelId")]
candidate_channels = {item["youtubeChannelId"] for item in candidate_items}
assert len(candidate_items) >= 1500, f"Expected at least 1500 source-backed movie candidates; found {len(candidate_items)}"
assert len(candidate_channels) >= 250, f"Expected at least 250 distinct candidate YouTube channels; found {len(candidate_channels)}"
search_items = [item for item in candidate_items if item.get("catalogEvidence", "").startswith("YouTube search result")]
feed_items = [item for item in candidate_items if item.get("catalogEvidence", "").startswith("YouTube channel RSS")]
assert len(search_items) >= 350 and all(item["durationMin"] >= 45 for item in search_items), "Search candidates need observed feature-length duration"
assert len(feed_items) >= 1200 and all(item["durationMin"] == 0 for item in feed_items), "RSS candidates must not invent unknown runtime"
assert all(item.get("rightsStatus") == "No verificado" for item in candidate_items), "Don't imply rights verification"
assert all("no verific" in item.get("synopsis", "").lower() for item in candidate_items), "Disclose candidate availability and rights status"
assert not (root / "app/src/main/assets/ia_catalog_v2.dat").exists(), "Legacy noisy Archive snapshot must be removed"
print(f"Validated {len(playlist_ids)} curated playlists, {len(search_items)} search results, {len(feed_items)} channel-feed candidates from {len(candidate_channels)} channels, and {len(items)} categorized offline videos")
