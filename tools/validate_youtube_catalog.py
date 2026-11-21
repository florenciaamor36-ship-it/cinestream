from pathlib import Path
import re
root = Path(__file__).resolve().parents[1]
source = (root / "app/src/main/java/com/example/data/repository/YouTubeMoviesCatalog.kt").read_text(encoding="utf-8")
rows = re.findall(r'PlaylistSource\("[^"\n]+", "([A-Za-z0-9_-]{10,})", "([^"]+)"\)', source)
ids = [row[0] for row in rows]
channels = {row[1].casefold() for row in rows}
assert len(ids) >= 6, f"Expected at least six curated YouTube playlists; found {len(ids)}"
assert len(ids) == len(set(ids)), "Playlist IDs must be unique"
assert len(channels) >= 5, f"Expected at least five publishers; found {len(channels)}"
assert "youtube.com/embed/" in source and "youtube.com/watch?v=" in source
assert "publisherMarker" in source and "fullMoviePattern" in source
assert not (root / "app/src/main/assets/ia_catalog_v2.dat").exists(), "Legacy noisy Archive snapshot must be removed"
print(f"Validated {len(ids)} curated YouTube playlists from {len(channels)} publishers; no Archive snapshot remains")
