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
        logger.error("TMDB_API_KEY no configurada; no se inventa metadata.")
        return None

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
            raise RuntimeError("El pool de Telegram no está activo; no se ejecuta un escaneo simulado.")

    finally:
        bot_pool.release_worker(worker)

    return ingested_count

