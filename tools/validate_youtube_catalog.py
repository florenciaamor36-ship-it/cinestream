from pathlib import Path
import json
import re

root = Path(__file__).resolve().parents[1]
source = (root / "app/src/main/java/com/example/data/repository/YouTubeMoviesCatalog.kt").read_text(encoding="utf-8")
rows = re.findall(r'PlaylistSource\("[^"\n]+", "([A-Za-z0-9_-]{10,})", "([^"]+)"\)', source)
playlist_ids = [row[0] for row in rows]
publishers = {row[1].casefold() for row in rows}
assert len(playlist_ids) >= 6, f"Expected at least six curated YouTube playlists; found {len(playlist_ids)}"
assert len(playlist_ids) == len(set(playlist_ids)), "Playlist IDs must be unique"
assert len(publishers) >= 5, f"Expected at least five publishers; found {len(publishers)}"
assert "youtube.com/feeds/videos.xml" in source
assert "https://www.youtube-nocookie.com/embed/" in source
assert "IOException" in source and "loadBundled()" in source
assert "publisherMarker" in source and "fullMoviePattern" in source
manifest = (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
assert 'android.permission.INTERNET' in manifest, "Android needs INTERNET permission for YouTube playback"

seed_path = root / "app/src/main/assets/youtube_movies_seed.json"
seed = json.loads(seed_path.read_text(encoding="utf-8"))
items = seed["items"]
assert len(items) >= 50, f"Expected at least 50 offline fallback movies; found {len(items)}"
video_ids = [item["streamUrl"].split("/embed/", 1)[-1].split("?", 1)[0] for item in items]
assert len(video_ids) == len(set(video_ids)), "Offline seed contains duplicate video IDs"
assert all(re.fullmatch(r"[A-Za-z0-9_-]{11}", video_id) for video_id in video_ids)
assert all(item["streamUrl"].startswith("https://www.youtube-nocookie.com/embed/") for item in items)
assert all(item["sourceUrl"].startswith("https://youtube.com/watch?v=") for item in items)
assert all(item["rating"] == -1.0 and item["releaseYear"] == 0 and item["durationMin"] == 0 for item in items), "Don't invent ratings, years, or runtimes"
assert not (root / "app/src/main/assets/ia_catalog_v2.dat").exists(), "Legacy noisy Archive snapshot must be removed"
print(f"Validated {len(playlist_ids)} curated playlists from {len(publishers)} publishers and {len(items)} offline fallback videos")
