import asyncio, json, os
import httpx
from sqlalchemy import delete
from database import AsyncSessionLocal, ProgramaEPG, init_db
from epg import parse_xmltv

async def sync_epg():
    await init_db(); cfg=json.load(open(os.path.join(os.path.dirname(__file__),'epg_sources.json'),encoding='utf8'))
    total=0
    async with httpx.AsyncClient(timeout=90, follow_redirects=True) as client:
        async with AsyncSessionLocal() as session:
            for source in cfg['sources']:
                if not source.get('enabled'): continue
                r=await client.get(source['url']); r.raise_for_status(); rows=parse_xmltv(r.content)
                ids={x['tvg_id'] for x in rows if x['tvg_id']}
                if ids: await session.execute(delete(ProgramaEPG).where(ProgramaEPG.tvg_id.in_(ids)))
                session.add_all(ProgramaEPG(**row) for row in rows if row['tvg_id'])
                total += len(rows)
            await session.commit()
    return total

if __name__=='__main__': print(f'Programas importados: {asyncio.run(sync_epg())}')
