import json
from pathlib import Path

path = Path(__file__).resolve().parents[1] / "app/src/main/assets/ia_public_domain_candidates.json"
data = json.loads(path.read_text(encoding="utf-8"))
items = data["items"]
assert data["candidateCount"] == 2300, data["candidateCount"]
assert len(items) == 2300, len(items)
assert len({item["identifier"] for item in items}) == 2300
for item in items:
    assert item["identifier"]
    assert item["licenseUrl"] == "http://creativecommons.org/publicdomain/mark/1.0/"
    assert item["rightsStatus"] == "DECLARED_PUBLIC_DOMAIN_MARK_REVIEW_REQUIRED"
    assert item["sourceUrl"].startswith("https://archive.org/details/")
    assert item["posterUrl"].startswith("https://archive.org/services/img/")
print(f"Validated {len(items)} unique declared-rights catalog candidates")
