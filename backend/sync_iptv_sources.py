"""Sincroniza las fuentes habilitadas de iptv_sources.json con la BD."""
import asyncio, json, os
import httpx
from database import AsyncSessionLocal, ListaM3U, CanalM3U, init_db
from m3u import parse_m3u

async def sync():
    await init_db()
    config = json.load(open(os.path.join(os.path.dirname(__file__), 'iptv_sources.json')))
    async with httpx.AsyncClient(timeout=45, follow_redirects=True, headers={'User-Agent':'CineStream IPTV importer/1.0'}) as client:
        async with AsyncSessionLocal() as session:
            total = 0
            for item in config['sources']:
                if not item.get('enabled'): continue
                response = await client.get(item['url']); response.raise_for_status()
                channels = parse_m3u(response.text)
                if not channels: continue
                source = ListaM3U(nombre=item['name'], url_fuente=item['url'])
                session.add(source); await session.flush()
                session.add_all(CanalM3U(lista_id=source.id, nombre=c.name, url=c.url, grupo=c.group, logo_url=c.logo, tvg_id=c.tvg_id) for c in channels)
                total += len(channels)
            await session.commit()
            print(f'Canales importados: {total}')

if __name__ == '__main__': asyncio.run(sync())
