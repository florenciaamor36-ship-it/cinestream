"""Sincroniza las fuentes habilitadas de iptv_sources.json con la BD."""
import asyncio, json, os
import httpx
from database import AsyncSessionLocal, ListaM3U, CanalM3U, init_db
from sqlalchemy import delete, select
from m3u import parse_m3u

CONFIG_PATH = os.path.join(os.path.dirname(__file__), "iptv_sources.json")

async def sync_enabled_sources() -> int:
    await init_db()
    config = json.load(open(CONFIG_PATH, encoding="utf-8"))
    total = 0
    async with httpx.AsyncClient(timeout=45, follow_redirects=True,
                                 headers={"User-Agent": "CineStream IPTV importer/1.0"}) as client:
        async with AsyncSessionLocal() as session:
            for item in config["sources"]:
                if not item.get("enabled"):
                    continue
                response = await client.get(item["url"])
                response.raise_for_status()
                channels = parse_m3u(response.text)
                if not channels:
                    continue
                old = await session.execute(select(ListaM3U).where(ListaM3U.url_fuente == item["url"]))
                for previous in old.scalars().all():
                    await session.execute(delete(CanalM3U).where(CanalM3U.lista_id == previous.id))
                    await session.delete(previous)
                source = ListaM3U(nombre=item["name"], url_fuente=item["url"])
                session.add(source)
                await session.flush()
                session.add_all(
                    CanalM3U(lista_id=source.id, nombre=c.name, url=c.url,
                             grupo=c.group, logo_url=c.logo, tvg_id=c.tvg_id)
                    for c in channels
                )
                total += len(channels)
            await session.commit()
    return total

if __name__ == "__main__":
    print(f"Canales importados: {asyncio.run(sync_enabled_sources())}")
