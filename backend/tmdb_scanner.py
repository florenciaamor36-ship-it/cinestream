"""
CineStream TMDb Metadata Scanner & Telegram Channel Ingestion
=============================================================
Escanea los mensajes de video en el canal privado de Telegram, extrae
los identificadores de archivo, limpia los títulos y consulta la API
de The Movie Database (TMDb) para poblar la base de datos automáticamente.
"""

import asyncio
import logging
import os
import re
from typing import Dict, Optional, Tuple
import httpx
from sqlalchemy import select

from database import AsyncSessionLocal, Pelicula, init_db
from bot_pool import bot_pool, PYROGRAM_AVAILABLE

logger = logging.getLogger("CineStream.Scanner")

TMDB_API_KEY = os.getenv("TMDB_API_KEY", "")
TMDB_BASE_URL = "https://api.themoviedb.org/3"
TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/w500"
TMDB_BACKDROP_BASE = "https://image.tmdb.org/t/p/original"

GENRE_MAP = {
    28: "Acción", 12: "Aventura", 16: "Animación", 35: "Comedia",
    80: "Crimen", 99: "Documental", 18: "Drama", 10751: "Familia",
    14: "Fantasía", 36: "Historia", 27: "Terror", 10402: "Música",
    9648: "Misterio", 10749: "Romance", 878: "Ciencia Ficción",
    10770: "Película de TV", 53: "Suspenso", 10752: "Guerra", 37: "Western"
}


def clean_title_and_extract_year(raw_text: str) -> Tuple[str, Optional[int]]:
    """
    Limpia tags típicos de rips de Telegram como:
    'Inception.2010.1080p.BluRay.x264-SPARKS.mp4' -> ('Inception', 2010)
    """
    if not raw_text:
        return ("Película Desconocida", 2024)

    # Eliminar extensión
    name = re.sub(r"\.(mp4|mkv|avi|mov|flv|webm)$", "", raw_text, flags=re.IGNORECASE)
    
    # Extraer año (1920-2029)
    year_match = re.search(r"[\s\.\(\[\-_](19\d\d|20\d\d)[\s\.\)\]\-_]", name)
    year = int(year_match.group(1)) if year_match else None

    # Limpiar palabras clave comunes en nombres de archivos
    cleaned = re.sub(
        r"[\.\-_](1080p|720p|4k|2160p|bluray|web-?dl|hdr|hevc|x264|x265|dvdrip|aac|latino|dual|castellano|sub)[\.\-_]?.*",
        "",
        name,
        flags=re.IGNORECASE
    )

    # Reemplazar puntos y guiones bajos por espacios
    cleaned = cleaned.replace(".", " ").replace("_", " ").strip()
    
    # Si quedó vacío o muy corto
    if len(cleaned) < 2:
        cleaned = raw_text[:30]

    return cleaned, year


async def query_tmdb_metadata(title: str, year: Optional[int] = None) -> Optional[Dict]:
    """
    Consulta la API de TMDb para obtener la sinopsis oficial, afiches en alta resolución y rating.
    """
    if not TMDB_API_KEY:
        logger.warning("TMDB_API_KEY no configurada. Generando metadata por defecto.")
        return {
            "title": title,
            "overview": f"Una emocionante historia que cautiva de principio a fin: {title}.",
            "poster_url": "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=600&auto=format&fit=crop&q=80",
            "backdrop_url": "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=1200&auto=format&fit=crop&q=80",
            "genre": "Acción",
            "rating": 8.4,
            "release_year": year or 2024
        }

    params = {
        "api_key": TMDB_API_KEY,
        "query": title,
        "language": "es-ES",
        "include_adult": False
    }
    if year:
        params["year"] = year

    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            resp = await client.get(f"{TMDB_BASE_URL}/search/movie", params=params)
            if resp.status_code == 200:
                data = resp.json()
                results = data.get("results", [])
                if results:
                    top = results[0]
                    genre_ids = top.get("genre_ids", [])
                    genre_name = GENRE_MAP.get(genre_ids[0], "Acción") if genre_ids else "Drama"
                    
                    poster_path = top.get("poster_path")
                    backdrop_path = top.get("backdrop_path")
                    
                    poster_url = f"{TMDB_IMAGE_BASE}{poster_path}" if poster_path else ""
                    backdrop_url = f"{TMDB_BACKDROP_BASE}{backdrop_path}" if backdrop_path else ""
                    
                    release_date = top.get("release_date", "")
                    parsed_year = int(release_date.split("-")[0]) if release_date and "-" in release_date else (year or 2024)

                    return {
                        "title": top.get("title") or title,
                        "overview": top.get("overview") or f"Sinopsis oficial de {title}.",
                        "poster_url": poster_url,
                        "backdrop_url": backdrop_url,
                        "genre": genre_name,
                        "rating": round(float(top.get("vote_average", 7.5)), 1),
                        "release_year": parsed_year
                    }
    except Exception as e:
        logger.error(f"Error consultando TMDb para '{title}': {e}")

    return None


async def scan_channel_and_enrich(channel_id: int, limit: int = 50) -> int:
    """
    Escanea los mensajes del canal de Telegram utilizando un worker del pool,
    extrae los videos y los registra en la base de datos enriquecidos con TMDb.
    """
    await init_db()
    await bot_pool.start_pool()

    worker = await bot_pool.get_next_worker()
    ingested_count = 0

    try:
        logger.info(f"Escaneando hasta {limit} mensajes del canal {channel_id} con Worker #{worker.bot_id}...")

        if worker.client and PYROGRAM_AVAILABLE:
            async for message in worker.client.get_chat_history(chat_id=channel_id, limit=limit):
                media = message.video or message.document
                if not media:
                    continue

                # Determinar si es video
                mime = getattr(media, "mime_type", "") or ""
                if not mime.startswith("video/") and not (media.file_name and media.file_name.lower().endswith((".mp4", ".mkv"))):
                    continue

                # Evitar duplicados
                async with AsyncSessionLocal() as session:
                    stmt = select(Pelicula).where(
                        Pelicula.canal_telegram_id == channel_id,
                        Pelicula.message_id_telegram == message.id
                    )
                    res = await session.execute(stmt)
                    if res.scalar_one_or_none():
                        continue  # Ya registrada

                raw_name = message.caption or media.file_name or f"Video {message.id}"
                clean_title, year = clean_title_and_extract_year(raw_name)
                
                # Consultar metadata en TMDb
                tmdb_data = await query_tmdb_metadata(clean_title, year)
                if not tmdb_data:
                    tmdb_data = {
                        "title": clean_title,
                        "overview": message.caption or f"Contenido multimedia #{message.id}",
                        "poster_url": "",
                        "backdrop_url": "",
                        "genre": "General",
                        "rating": 7.5,
                        "release_year": year or 2024
                    }

                # Guardar en base de datos
                async with AsyncSessionLocal() as session:
                    peli = Pelicula(
                        titulo=tmdb_data["title"],
                        sinopsis=tmdb_data["overview"],
                        poster_url=tmdb_data["poster_url"],
                        backdrop_url=tmdb_data["backdrop_url"],
                        genero=tmdb_data["genre"],
                        canal_telegram_id=channel_id,
                        message_id_telegram=message.id,
                        file_id_telegram=media.file_id,
                        duracion_min=getattr(media, "duration", 0) // 60 or 115,
                        anio_estreno=tmdb_data["release_year"],
                        calificacion=tmdb_data["rating"],
                        tamano_bytes=media.file_size,
                        calidad_video="1080p" if (getattr(media, "height", 0) or 0) >= 1080 else "720p",
                        mime_type=mime or "video/mp4",
                        es_destacada=ingested_count == 0  # La primera como destacada
                    )
                    session.add(peli)
                    await session.commit()
                    ingested_count += 1
                    logger.info(f"Ingestada con éxito: {peli.titulo} (ID Telegram msg: {message.id})")

        else:
            logger.info("Modo de desarrollo activo: poblando catálogo inicial de películas de demostración.")
            ingested_count = await seed_sample_movies()

    finally:
        bot_pool.release_worker(worker)

    return ingested_count


async def seed_sample_movies() -> int:
    """Inserta películas de demostración si la base de datos está vacía."""
    sample_movies = [
        {
            "titulo": "Cyber Horizon: 2099",
            "sinopsis": "En una metrópolis dominada por megacorporaciones y redes cuánticas, un detective cibernético descubre una señal oculta que amenaza con desconectar la conciencia de la humanidad entera.",
            "poster_url": "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600&auto=format&fit=crop&q=80",
            "backdrop_url": "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=1200&auto=format&fit=crop&q=80",
            "genero": "Ciencia Ficción",
            "anio": 2025,
            "calificacion": 8.9,
            "duracion": 138,
            "destacada": True,
            "msg_id": 101
        },
        {
            "titulo": "Sombra en la Niebla",
            "sinopsis": "Un agente encubierto en el puerto de Róterdam debe infiltrarse en un sindicato internacional de contrabando sin ser detectado por los algoritmos de vigilancia biométrica.",
            "poster_url": "https://images.unsplash.com/photo-1509281373149-e957c6296406?w=600&auto=format&fit=crop&q=80",
            "backdrop_url": "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=1200&auto=format&fit=crop&q=80",
            "genero": "Acción",
            "anio": 2024,
            "calificacion": 8.3,
            "duracion": 115,
            "destacada": False,
            "msg_id": 102
        },
        {
            "titulo": "El Eco del Silencio",
            "sinopsis": "Tras un misterioso cataclismo acústico que destruyó las telecomunicaciones globales, dos supervivientes cruzan el macizo alpino guiados por frecuencias de radio analógicas.",
            "poster_url": "https://images.unsplash.com/photo-1440404653325-ab127d49abc1?w=600&auto=format&fit=crop&q=80",
            "backdrop_url": "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=1200&auto=format&fit=crop&q=80",
            "genero": "Drama",
            "anio": 2024,
            "calificacion": 8.6,
            "duracion": 124,
            "destacada": False,
            "msg_id": 103
        },
        {
            "titulo": "Código Rojo: Vuelo 814",
            "sinopsis": "A 35,000 pies de altura, un grupo de piratas informáticos toma el control de los sistemas de navegación de un avión comercial transoceánico.",
            "poster_url": "https://images.unsplash.com/photo-1436491865332-7a61a109cc05?w=600&auto=format&fit=crop&q=80",
            "backdrop_url": "https://images.unsplash.com/photo-1519074069444-1ba4ea16e6f6?w=1200&auto=format&fit=crop&q=80",
            "genero": "Suspenso",
            "anio": 2025,
            "calificacion": 7.9,
            "duracion": 108,
            "destacada": False,
            "msg_id": 104
        },
        {
            "titulo": "Reinos Perdidos: La Reliquia",
            "sinopsis": "Una expedición arqueológica en los desiertos del Kalahari descubre una bóveda prehistórica que contiene tecnología que desafía las leyes físicas contemporáneas.",
            "poster_url": "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600&auto=format&fit=crop&q=80",
            "backdrop_url": "https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?w=1200&auto=format&fit=crop&q=80",
            "genero": "Aventura",
            "anio": 2023,
            "calificacion": 8.1,
            "duracion": 142,
            "destacada": False,
            "msg_id": 105
        },
        {
            "titulo": "Destino interestelar: Alfa",
            "sinopsis": "La tripulación del crucero de colonización Génesis despierta de la hipersuspensión cuando la inteligencia artificial del navío detecta una megaestructura extraterrestre.",
            "poster_url": "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=600&auto=format&fit=crop&q=80",
            "backdrop_url": "https://images.unsplash.com/photo-1446776811953-b23d57bd21aa?w=1200&auto=format&fit=crop&q=80",
            "genero": "Ciencia Ficción",
            "anio": 2026,
            "calificacion": 9.1,
            "duracion": 156,
            "destacada": False,
            "msg_id": 106
        }
    ]

    async with AsyncSessionLocal() as session:
        # Verificar si ya existen películas
        stmt = select(Pelicula)
        res = await session.execute(stmt)
        if len(res.scalars().all()) > 0:
            return 0

        for item in sample_movies:
            peli = Pelicula(
                titulo=item["titulo"],
                sinopsis=item["sinopsis"],
                poster_url=item["poster_url"],
                backdrop_url=item["backdrop_url"],
                genero=item["genero"],
                canal_telegram_id=-1001987654321,
                message_id_telegram=item["msg_id"],
                duracion_min=item["duracion"],
                anio_estreno=item["anio"],
                calificacion=item["calificacion"],
                tamano_bytes=1_500_000_000,
                calidad_video="4K Ultra HD",
                mime_type="video/mp4",
                es_destacada=item["destacada"]
            )
            session.add(peli)
        await session.commit()
        return len(sample_movies)


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    channel = int(os.getenv("TELEGRAM_CHANNEL_ID", "-1001987654321"))
    asyncio.run(scan_channel_and_enrich(channel, limit=20))
