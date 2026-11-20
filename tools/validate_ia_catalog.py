import gzip
import json
from pathlib import Path

path = Path(__file__).resolve().parents[1] / "app/src/main/assets/ia_catalog_v2.dat"
with gzip.open(path, "rt", encoding="utf-8") as stream:
    data = json.load(stream)
items = data["items"]
assert data["sourceRecordCount"] == 29634, data["sourceRecordCount"]
assert 29000 <= len(items) <= 29634, len(items)
assert len({item["identifier"] for item in items}) == len(items)
assert any(item["rightsStatus"] == "DECLARED_LICENSE_REVIEW_REQUIRED" for item in items)
assert any(item["rightsStatus"] == "NO_LICENSE_DECLARATION_FOUND" for item in items)
for item in items:
    assert item["identifier"]
    assert item["rightsStatus"] in {"DECLARED_LICENSE_REVIEW_REQUIRED", "NO_LICENSE_DECLARATION_FOUND"}
    assert item["sourceUrl"].startswith("https://archive.org/details/")
    assert item["posterUrl"].startswith("https://archive.org/services/img/")
print(f"Validated {len(items)} unique records from {data['sourceRecordCount']} Archive hits")
