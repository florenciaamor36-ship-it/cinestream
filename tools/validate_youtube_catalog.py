from pathlib import Path
import json
import re

root = Path(__file__).resolve().parents[1]
search_source = (root / "app/src/main/java/com/example/data/youtube/YouTubeSearchCatalog.kt").read_text(encoding="utf-8")
catalog_source = (root / "app/src/main/java/com/example/data/repository/YouTubeMoviesCatalog.kt").read_text(encoding="utf-8")
assert "SearchInfo.getInfo" in search_source and "SearchInfo.getMoreItems" in search_source, "Live discovery must use the bundled NewPipe engine and paginate results"
assert "MAX_PAGES_PER_QUERY = 4" in search_source
assert "durationSeconds < 45 * 60" in search_source, "Reject results shorter than feature-length"
assert "fullLengthPattern.containsMatchIn(text)" in search_source and "spanishPattern.containsMatchIn(text)" in search_source
assert "rejectPattern.containsMatchIn(title)" in search_source, "Reject trailers, clips, shorts and episodes"
assert "YouTubeSearchCatalog().searchMovies()" in catalog_source, "The catalog must use the extractor engine"
assert "parseFeed" not in catalog_source and "feeds/videos.xml" not in catalog_source, "Do not inflate the catalog with undurationed RSS candidates"
assert "loadBundled()" in catalog_source

seed_path = root / "app/src/main/assets/youtube_movies_seed.json"
seed = json.loads(seed_path.read_text(encoding="utf-8"))
items = seed["items"]
assert len(items) >= 475, f"Expected the filtered offline catalog; found {len(items)}"
video_ids = [item["streamUrl"].split("/embed/", 1)[-1].split("?", 1)[0] for item in items]
assert len(video_ids) == len(set(video_ids)), "Offline seed contains duplicate video IDs"
assert all(re.fullmatch(r"[A-Za-z0-9_-]{11}", video_id) for video_id in video_ids)
assert all(item["streamUrl"].startswith("https://www.youtube-nocookie.com/embed/") for item in items)
assert all(item.get("genre") for item in items), "Every fallback movie needs a category"
assert all(item["sourceUrl"].startswith("https://youtube.com/watch?v=") for item in items)
assert all(item["rating"] == -1.0 and item["releaseYear"] == 0 for item in items), "Don't invent ratings or years"
full_movie = re.compile(r"(?iu)(pel[ií]cula(s)?\s+(completa(s)?|entera(s)?|\\bfull\\b)|full\s+(length\s+)?movie|full\s+film|feature\s+film|largometraje|filme\s+completo|pellicola\s+completa|subt[ií]tulos?\s+en\s+espa[nñ]ol|subtitles?\s+in\s+spanish)")
spanish = re.compile(r"(?iu)(espa[nñ]ol|spanish|castellano|latino|latina|doblad[oa]s?|subt[ií]tulos?\s+en\s+espa[nñ]ol|subtitles?\s+in\s+spanish)")
assert all(full_movie.search(item["title"] + " " + item.get("synopsis", "")) for item in items), "Seed has a record without a full-movie clue"
assert all(spanish.search(item["title"] + " " + item.get("synopsis", "")) for item in items), "Seed has a record without a Spanish-language clue"
search_items = [item for item in items if item.get("catalogEvidence", "").startswith("YouTube search result")]
curated_items = [item for item in items if item.get("catalogEvidence", "").startswith("Curated playlist entry")]
assert len(search_items) >= 350 and all(item["durationMin"] >= 45 for item in search_items), "Engine-searched seed candidates need observed feature-length duration"
assert len(curated_items) >= 100 and all(item["durationMin"] == 0 for item in curated_items), "Curated feed candidates must not claim unknown runtime"
assert not any(item.get("catalogEvidence", "").startswith("YouTube channel RSS") for item in items), "Undurationed channel RSS search results must not be listed as films"
assert all(item.get("rightsStatus") == "No verificado" for item in items if "rightsStatus" in item), "Don't imply rights verification"
channels = {item["youtubeChannelId"] for item in search_items if item.get("youtubeChannelId")}
assert len(channels) >= 250, f"Expected distinct source channels, found {len(channels)}"
print(f"Validated NewPipe paginated movie filter, {len(search_items)} duration-filtered engine-search films, {len(curated_items)} curated seed films, {len(channels)} search-result channels, and {len(items)} total offline entries")
