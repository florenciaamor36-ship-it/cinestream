"""Parser y almacenamiento de listas M3U autorizadas."""
from dataclasses import dataclass
from typing import Iterable
from urllib.parse import urlparse

@dataclass
class M3UChannel:
    name: str
    url: str
    group: str = "Sin categoría"
    logo: str = ""
    tvg_id: str = ""
    country: str = ""
    language: str = ""
    description: str = ""


def parse_m3u(text: str) -> list[M3UChannel]:
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    channels: list[M3UChannel] = []
    pending: dict[str, str] = {}
    for line in lines:
        if line.startswith("#EXTINF"):
            attrs = {}
            head, _, title = line.partition(",")
            for key, value in __import__("re").findall(r'(\w[\w-]*)="([^"]*)"', head):
                attrs[key.lower()] = value
            pending = {
                "name": title.strip() or attrs.get("tvg-name", "Sin nombre"),
                "group": attrs.get("group-title", "Sin categoría"),
                "logo": attrs.get("tvg-logo", ""),
                "tvg_id": attrs.get("tvg-id", ""),
                "country": attrs.get("tvg-country", ""),
                "language": attrs.get("tvg-language", ""),
                "description": attrs.get("tvg-description", ""),
            }
        elif not line.startswith("#") and pending:
            parsed = urlparse(line)
            if parsed.scheme in {"http", "https"}:
                channels.append(M3UChannel(url=line, **pending))
            pending = {}
    return channels
