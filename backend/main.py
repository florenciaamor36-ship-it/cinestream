"""
CineStream Backend API & HTTP Streaming Server (FastAPI)
========================================================
Servidor de streaming HTTP de alto rendimiento con:
- Soporte para HTTP Range Requests (RFC 7233) para buffering progresivo y rebobinado en Android.
- Conexión al Worker Pool de 20 bots de Telegram (toda la infraestructura de Telegram oculta).
- Endpoints REST para catálogo de películas, géneros y métricas de los workers.
"""

import logging
import os
import re
from contextlib import asynccontextmanager
from typing import Optional, List

from fastapi import FastAPI, HTTPException, Request, Response, status, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse, JSONResponse
from sqlalchemy import select

from database import AsyncSessionLocal, Pelicula, ListaM3U, CanalM3U, init_db
from bot_pool import bot_pool
from tmdb_scanner import scan_channel_and_enrich
from m3u import parse_m3u
from sync_iptv_sources import sync_enabled_sources
from datetime import datetime
import httpx
from youtube_live import youtube_live_manager

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger("CineStream.Server")


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Ciclo de vida de FastAPI: arranca BD, seed y Worker Pool de bots."""
    logger.info("Iniciando CineStream Streaming Engine...")
    await init_db()
    if os.getenv("SYNC_IPTV_ON_STARTUP", "true").lower() == "true":
        try:
            imported = await sync_enabled_sources()
            logger.info("IPTV sincronizado: %s canales.", imported)
        except Exception as exc:
            logger.error("No se pudo sincronizar IPTV al iniciar: %s", exc)
    if os.getenv("START_TELEGRAM_POOL", "false").lower() == "true":
        await bot_pool.start_pool()
        logger.info("Pool real de Telegram iniciado.")
    else:
        logger.info("Pool de Telegram desactivado: configure START_TELEGRAM_POOL=true cuando existan credenciales reales.")
    logger.info("CineStream Backend operativo sin datos simulados.")
    yield
    if bot_pool._initialized:
        await bot_pool.stop_pool()


app = FastAPI(
    title="CineStream API & Telegram Bot Farm Streaming Server",
    description="Backend REST y Streaming HTTP para app móvil Android tipo Netflix respaldado por 20 bots de Telegram.",
    version="1.0.0",
    lifespan=lifespan
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


def parse_range_header(range_header: Optional[str], file_size: int) -> tuple[int, int]:
    """
    Parsea la cabecera HTTP 'Range: bytes=start-end'.
    Devuelve la tupla (start_byte, end_byte).
    """
    if not range_header:
        return 0, file_size - 1

    match = re.match(r"bytes=(\d+)-(\d*)", range_header.strip())
    if not match:
        return 0, file_size - 1

    start_str, end_str = match.groups()
    start = int(start_str) if start_str else 0
    end = int(end_str) if end_str else file_size - 1

    if start >= file_size or end >= file_size or start > end:
        raise HTTPException(
            status_code=status.HTTP_416_REQUESTED_RANGE_NOT_SATISFIABLE,
            detail=f"Rango solicitado {range_header} no es válido para tamaño de archivo {file_size} bytes."
        )

    return start, end


@app.get("/stream/{id_pelicula}")
async def stream_movie(
    id_pelicula: int,
    request: Request
):
    """
    ENDPOINT PRINCIPAL DE STREAMING DE VIDEO (RFC 7233):
    Consume las peticiones HTTP Range de Android Media3 / ExoPlayer y descarga los
    fragmentos en tiempo real mediante el pool de 20 bots de Telegram.
    El usuario y la app móvil NUNCA ven URLs de Telegram ni tokens.
    """
    # 1. Consultar registro de la película en la base de datos
    async with AsyncSessionLocal() as session:
        stmt = select(Pelicula).where(Pelicula.id_pelicula == id_pelicula)
        result = await session.execute(stmt)
        movie = result.scalar_one_or_none()

    if not movie:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Película con ID {id_pelicula} no encontrada."
        )

    # 2. Determinar tamaño total del archivo multimedia
    file_size = movie.tamano_bytes or 1_073_741_824  # 1 GB por defecto

    # 3. Parsear cabecera Range
    range_header = request.headers.get("Range")
    start_byte, end_byte = parse_range_header(range_header, file_size)

    content_length = (end_byte - start_byte) + 1

    logger.info(
        f"[Stream Request] Película '{movie.titulo}' (ID {movie.id_pelicula}) | "
        f"Rango: {start_byte}-{end_byte}/{file_size} ({content_length} bytes) | "
        f"Client IP: {request.client.host if request.client else 'Unknown'}"
    )

    # 4. Cabeceras HTTP estándar para reproducción progresiva fluida en ExoPlayer
    headers = {
        "Content-Range": f"bytes {start_byte}-{end_byte}/{file_size}",
        "Accept-Ranges": "bytes",
        "Content-Length": str(content_length),
        "Content-Type": movie.mime_type or "video/mp4",
        "Cache-Control": "public, max-age=3600",
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Expose-Headers": "Content-Range, Accept-Ranges, Content-Length",
        "X-Worker-Farm": "Telegram-Bot-Pool-20"
    }

    # 5. Generador asíncrono que consulta el pool de bots de Telegram
    media_generator = bot_pool.stream_media_range(
        channel_id=movie.canal_telegram_id,
        message_id=movie.message_id_telegram,
        start_byte=start_byte,
        end_byte=end_byte,
        chunk_size=1024 * 512  # 512 KB por bloque de stream
    )

    return StreamingResponse(
        media_generator,
        status_code=status.HTTP_206_PARTIAL_CONTENT,
        headers=headers,
        media_type=movie.mime_type or "video/mp4"
    )


# ==============================================================================
# ENDPOINTS DEL CATÁLOGO PARA LA APP ANDROID
# ==============================================================================

@app.get("/api/movies")
async def get_movies(
    request: Request,
    genero: Optional[str] = Query(None, description="Filtrar por género"),
    search: Optional[str] = Query(None, description="Buscar por título o sinopsis"),
    destacadas: Optional[bool] = Query(None, description="Filtrar destacadas")
):
    """Devuelve la lista de películas disponibles enriquecidas con TMDb."""
    base_url = str(request.base_url).rstrip("/")
    async with AsyncSessionLocal() as session:
        stmt = select(Pelicula)
        if genero and genero.lower() != "todos":
            stmt = stmt.where(Pelicula.genero.ilike(f"%{genero}%"))
        if search:
            stmt = stmt.where(
                (Pelicula.titulo.ilike(f"%{search}%")) |
                (Pelicula.sinopsis.ilike(f"%{search}%"))
            )
        if destacadas is not None:
            stmt = stmt.where(Pelicula.es_destacada == destacadas)

        stmt = stmt.order_by(Pelicula.id_pelicula.asc())
        result = await session.execute(stmt)
        movies = result.scalars().all()

    return [m.to_dict(base_stream_url=base_url) for m in movies]


@app.get("/api/movies/{id_pelicula}")
async def get_movie_detail(id_pelicula: int, request: Request):
    """Devuelve los detalles de una película específica con su stream_url."""
    base_url = str(request.base_url).rstrip("/")
    async with AsyncSessionLocal() as session:
        stmt = select(Pelicula).where(Pelicula.id_pelicula == id_pelicula)
        result = await session.execute(stmt)
        movie = result.scalar_one_or_none()

    if not movie:
        raise HTTPException(status_code=404, detail="Película no encontrada.")

    return movie.to_dict(base_stream_url=base_url)


@app.get("/api/genres")
async def get_genres():
    """Devuelve la lista de géneros disponibles en el catálogo."""
    async with AsyncSessionLocal() as session:
        stmt = select(Pelicula.genero).distinct()
        result = await session.execute(stmt)
        genres = [g[0] for g in result.all() if g[0]]

    return ["Todos"] + sorted(list(set(genres)))


def require_admin(request: Request):
    expected = os.getenv("ADMIN_API_KEY", "")
    supplied = request.headers.get("X-Admin-Key", "")
    if not expected or supplied != expected:
        raise HTTPException(status_code=401, detail="Admin API key inválida o no configurada")


@app.get("/api/iptv/channels")
async def get_iptv_channels(
    grupo: Optional[str] = Query(None),
    search: Optional[str] = Query(None),
):
    async with AsyncSessionLocal() as session:
        stmt = select(CanalM3U).where(CanalM3U.activo == True)
        if grupo and grupo.lower() != "todos":
            stmt = stmt.where(CanalM3U.grupo.ilike(f"%{grupo}%"))
        if search:
            stmt = stmt.where(CanalM3U.nombre.ilike(f"%{search}%"))
        result = await session.execute(stmt.order_by(CanalM3U.nombre.asc()))
        return [{"id": c.id, "nombre": c.nombre, "url": c.url, "grupo": c.grupo,
                 "logo_url": c.logo_url or "", "tvg_id": c.tvg_id or "",
                 "pais": c.pais or "", "idioma": c.idioma or "",
                 "descripcion": c.descripcion or ""}
                for c in result.scalars().all()]


@app.post("/api/admin/iptv/import")
async def import_iptv_list(request: Request, nombre: str = Query(..., min_length=1), url: Optional[str] = Query(None)):
    require_admin(request)
    raw = await request.body()
    if url:
        async with httpx.AsyncClient(timeout=30, follow_redirects=True) as client:
            response = await client.get(url)
            response.raise_for_status()
            raw = response.content
    if not raw:
        raise HTTPException(status_code=400, detail="La lista M3U está vacía")
    channels = parse_m3u(raw.decode("utf-8", errors="replace"))
    if not channels:
        raise HTTPException(status_code=422, detail="No se encontraron canales HTTP/HTTPS válidos")
    async with AsyncSessionLocal() as session:
        source = ListaM3U(nombre=nombre, url_fuente=url, ultima_actualizacion=datetime.utcnow())
        session.add(source)
        await session.flush()
        session.add_all([CanalM3U(lista_id=source.id, nombre=c.name, url=c.url, grupo=c.group,
                                  logo_url=c.logo, tvg_id=c.tvg_id) for c in channels])
        await session.commit()
        return {"status": "success", "lista_id": source.id, "canales_importados": len(channels)}


@app.get("/api/status")
async def get_server_status():
    """Métricas en tiempo real del estado de los 20 bots de Telegram."""
    pool_telemetry = bot_pool.get_pool_status()
    return {
        "status": "online",
        "service": "CineStream Core Streaming Server",
        "telegram_worker_pool": pool_telemetry
    }


@app.post("/api/scan")
async def trigger_scan(
    limit: int = Query(30, ge=1, le=100)
):
    """Inicia el escaneo y parseo del canal de Telegram con TMDb en segundo plano."""
    raw_channel_id = os.getenv("TELEGRAM_CHANNEL_ID")
    if not raw_channel_id:
        raise HTTPException(status_code=503, detail="TELEGRAM_CHANNEL_ID no está configurado")
    channel_id = int(raw_channel_id)
    ingested = await scan_channel_and_enrich(channel_id, limit=limit)
    return {
        "status": "success",
        "message": f"Escaneo completado. Se ingestaron {ingested} títulos nuevos.",
        "channel_id": channel_id
    }


# ==============================================================================
# ENDPOINTS DE CANALES EN VIVO (YOUTUBE LIVE HLS STREAMING)
# ==============================================================================

@app.get("/api/live/channels")
async def get_live_channels(request: Request):
    """
    Devuelve el catálogo de canales en vivo de YouTube disponibles.
    Incluye URLs de reproducción master.m3u8 compatibles con ExoPlayer.
    """
    base_url = str(request.base_url).rstrip("/")
    return youtube_live_manager.get_channels_list(base_url=base_url)


@app.get("/api/live/{channel_id}/master.m3u8")
async def stream_live_channel(
    channel_id: str,
    youtube_url: Optional[str] = Query(None, description="URL opcional directa de YouTube Live"),
    request: Request = None
):
    """
    Resuelve o entrega desde la caché en memoria (TTL 45 min) el manifiesto HLS (.m3u8)
    de una transmisión en vivo de YouTube para reproducir directamente en Android ExoPlayer.
    """
    target = youtube_url if youtube_url else channel_id
    hls_url = await youtube_live_manager.get_live_stream_url(target)

    # Redirige a ExoPlayer hacia el stream HLS directo obtenido del CDN
    # con soporte de encabezados CORS y MIME Type application/vnd.apple.mpegurl
    from fastapi.responses import RedirectResponse
    return RedirectResponse(
        url=hls_url,
        status_code=status.HTTP_307_TEMPORARY_REDIRECT,
        headers={
            "Access-Control-Allow-Origin": "*",
            "Cache-Control": "public, max-age=60",
            "Content-Type": "application/vnd.apple.mpegurl"
        }
    )


@app.post("/api/live/resolve")
async def resolve_custom_youtube_live(
    youtube_url: str = Query(..., description="URL de YouTube Live para resolver"),
    request: Request = None
):
    """
    Resuelve una URL personalizada de transmisión en vivo de YouTube y la almacena
    en caché en memoria por 45 minutos.
    """
    hls_url = await youtube_live_manager.get_live_stream_url(youtube_url)
    return {
        "status": "success",
        "youtube_url": youtube_url,
        "hls_stream_url": hls_url,
        "ttl_minutes": 45
    }


if __name__ == "__main__":
    import uvicorn
    host = os.getenv("SERVER_HOST", "0.0.0.0")
    port = int(os.getenv("SERVER_PORT", "8000"))
    logger.info(f"Levantando CineStream Server en {host}:{port}...")
    uvicorn.run("main:app", host=host, port=port, reload=False, workers=1)
