"""Validación de canales IPTV sin tocar la APK."""
import asyncio
from urllib.parse import urlparse
import httpx

async def probe(url: str, client: httpx.AsyncClient, sem: asyncio.Semaphore) -> bool:
    async with sem:
        try:
            r = await client.get(url, headers={"Range": "bytes=0-1023"}, timeout=8)
            if r.status_code not in (200, 206):
                return False
            content = r.content[:1024].lstrip()
            content_type = (r.headers.get("content-type") or "").lower()
            return content.startswith(b"#EXTM3U") or "mpegurl" in content_type or "octet-stream" in content_type
        except Exception:
            return False

async def validate_channels(channels: list, concurrency: int = 40) -> list:
    unique = {}
    for channel in channels:
        url = channel.url.strip()
        parsed = urlparse(url)
        if parsed.scheme in {"http", "https"} and parsed.netloc:
            unique.setdefault(url, channel)
    sem = asyncio.Semaphore(concurrency)
    async with httpx.AsyncClient(follow_redirects=True, headers={"User-Agent": "CineStream IPTV validator/1.0"}) as client:
        checks = await asyncio.gather(*(probe(url, client, sem) for url in unique))
    return [channel for channel, ok in zip(unique.values(), checks) if ok]
