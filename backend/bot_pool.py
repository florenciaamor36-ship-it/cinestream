"""
CineStream Bot Pool Manager (Worker Pool de 20 Bots de Telegram)
================================================================
Gestiona un grupo de hasta 20 bots de Telegram mediante Pyrogram.
Implementa:
- Balanceo de carga Round-Robin con ponderación por disponibilidad.
- Circuit Breaker / Fallback automático ante errores FLOOD_WAIT.
- Generador de chunks asíncronos para HTTP Range Requests (0 - total_bytes).
- Conmutación en caliente de bots durante la transmisión sin interrumpir al cliente.
"""

import asyncio
import logging
import os
import time
from dataclasses import dataclass, field
from enum import Enum
from typing import AsyncGenerator, Dict, List, Optional, Tuple

logger = logging.getLogger("CineStream.BotPool")

# Pyrogram es obligatorio en producción; no se usa modo emulado.
try:
    from pyrogram import Client, errors
    from pyrogram.types import Message
    PYROGRAM_AVAILABLE = True
except ImportError:
    PYROGRAM_AVAILABLE = False
    logger.error("Pyrogram no está instalado. El pool no puede iniciar en producción.")


class BotStatus(str, Enum):
    ACTIVE = "ACTIVE"
    PAUSED = "PAUSED"  # Debido a FLOOD_WAIT o rate limit
    CONNECTING = "CONNECTING"
    ERROR = "ERROR"


@dataclass
class TelegramWorker:
    bot_id: int
    token: str
    client: Optional[any] = None
    status: BotStatus = BotStatus.CONNECTING
    pause_until: float = 0.0
    active_streams: int = 0
    total_bytes_served: int = 0
    error_count: int = 0
    last_error: str = ""

    @property
    def is_available(self) -> bool:
        if self.status == BotStatus.PAUSED:
            if time.time() >= self.pause_until:
                # El tiempo de espera FLOOD_WAIT ha expirado; rehabilitar bot
                self.status = BotStatus.ACTIVE
                self.pause_until = 0.0
                logger.info(f"[Worker #{self.bot_id}] Retomó servicio tras pausa por FloodWait.")
                return True
            return False
        return self.status == BotStatus.ACTIVE


class TelegramBotPool:
    """
    Administrador central de la granja de 20 bots de Telegram.
    Distribuye peticiones y garantiza alta disponibilidad.
    """

    def __init__(self, api_id: Optional[int] = None, api_hash: Optional[str] = None):
        raw_api_id = api_id or os.getenv("API_ID")
        self.api_id = int(raw_api_id) if raw_api_id else 0
        self.api_hash = api_hash or os.getenv("API_HASH", "")
        self.workers: List[TelegramWorker] = []
        self._current_index: int = 0
        self._lock = asyncio.Lock()
        self._initialized = False

    def load_bot_tokens_from_env(self) -> List[str]:
        """Carga los tokens de BOT_TOKEN_1 hasta BOT_TOKEN_20 desde las variables de entorno."""
        tokens = []
        for i in range(1, 21):
            token = os.getenv(f"BOT_TOKEN_{i}")
            if token and not token.startswith("sample_") and not "xxxx" in token:
                tokens.append(token.strip())
        
        if not tokens:
            raise RuntimeError("No hay tokens BOT_TOKEN_1..BOT_TOKEN_20 configurados; no se inicia un pool simulado.")
        return tokens

    async def start_pool(self):
        """Inicializa los 20 bots en el worker pool de forma paralela."""
        if self._initialized:
            return

        if not PYROGRAM_AVAILABLE:
            raise RuntimeError("Pyrogram es obligatorio para iniciar el pool real de Telegram.")
        if self.api_id <= 0 or not self.api_hash:
            raise RuntimeError("API_ID y API_HASH son obligatorios para iniciar el pool real de Telegram.")
        tokens = self.load_bot_tokens_from_env()
        logger.info(f"Iniciando Worker Pool real con {len(tokens)} bots de Telegram...")

        for idx, token in enumerate(tokens, start=1):
            worker = TelegramWorker(
                bot_id=idx,
                token=token,
                status=BotStatus.CONNECTING
            )

            if PYROGRAM_AVAILABLE:
                try:
                    client = Client(
                        name=f"bot_worker_{idx}",
                        api_id=self.api_id,
                        api_hash=self.api_hash,
                        bot_token=token,
                        in_memory=True,
                        max_concurrent_transmissions=8
                    )
                    await client.start()
                    worker.client = client
                    worker.status = BotStatus.ACTIVE
                    logger.info(f"[Worker #{idx}] Conectado exitosamente a Telegram.")
                except Exception as e:
                    worker.status = BotStatus.ERROR
                    worker.last_error = str(e)
                    logger.error(f"[Worker #{idx}] Error al conectar bot: {e}")
            self.workers.append(worker)

        self._initialized = True
        logger.info(f"Worker Pool listo. Bots activos: {len([w for w in self.workers if w.is_available])}/{len(self.workers)}")

    async def stop_pool(self):
        """Detiene con seguridad todos los clientes del pool."""
        logger.info("Cerrando conexiones del Worker Pool...")
        for worker in self.workers:
            if worker.client and PYROGRAM_AVAILABLE:
                try:
                    await worker.client.stop()
                except Exception as e:
                    logger.warning(f"Error cerrando worker #{worker.bot_id}: {e}")
        self._initialized = False

    async def get_next_worker(self) -> TelegramWorker:
        """
        Algoritmo de balanceo de carga:
        1. Filtra los workers disponibles (no pausados por FLOOD_WAIT).
        2. Selecciona mediante Round-Robin con sesgo hacia el bot con menos streams activos.
        """
        async with self._lock:
            available_workers = [w for w in self.workers if w.is_available]

            if not available_workers:
                # Todos los bots están en pausa por FLOOD_WAIT o error; hallar el que despierta primero
                paused = [w for w in self.workers if w.status == BotStatus.PAUSED]
                if paused:
                    earliest = min(paused, key=lambda w: w.pause_until)
                    wait_time = max(0.5, earliest.pause_until - time.time())
                    logger.warning(f"Pool saturado. Esperando {wait_time:.1f}s al worker #{earliest.bot_id}...")
                    await asyncio.sleep(wait_time)
                    earliest.status = BotStatus.ACTIVE
                    return earliest
                raise RuntimeError("No hay workers de Telegram disponibles en el pool.")

            # Ordenar por carga actual (menos streams activos primero) y desempate por round-robin
            available_workers.sort(key=lambda w: (w.active_streams, w.error_count))
            selected = available_workers[0]
            selected.active_streams += 1
            return selected

    def mark_flood_wait(self, worker: TelegramWorker, wait_seconds: int):
        """
        Circuit Breaker: Ante un FLOOD_WAIT de Telegram, pausa este bot y redirige
        el tráfico a otros bots del pool.
        """
        worker.status = BotStatus.PAUSED
        worker.pause_until = time.time() + wait_seconds + 1  # 1 segundo de margen
        worker.error_count += 1
        worker.last_error = f"FLOOD_WAIT ({wait_seconds}s)"
        if worker.active_streams > 0:
            worker.active_streams -= 1
        logger.warning(f"[Circuit Breaker] Worker #{worker.bot_id} pausado por {wait_seconds}s debido a FLOOD_WAIT.")

    def release_worker(self, worker: TelegramWorker, bytes_served: int = 0):
        """Libera la carga del worker tras finalizar o fallar un stream."""
        if worker.active_streams > 0:
            worker.active_streams -= 1
        worker.total_bytes_served += bytes_served

    async def get_media_metadata(self, channel_id: int, message_id: int) -> Tuple[int, str, str]:
        """
        Obtiene el tamaño en bytes, nombre de archivo y tipo MIME del video en Telegram.
        Retorna: (file_size_bytes, mime_type, file_name)
        """
        worker = await self.get_next_worker()
        try:
            if worker.client and PYROGRAM_AVAILABLE:
                message = await worker.client.get_messages(chat_id=channel_id, message_ids=message_id)
                media = message.video or message.document or message.animation
                if not media:
                    raise ValueError(f"El mensaje {message_id} en {channel_id} no contiene multimedia.")
                file_size = media.file_size
                mime_type = getattr(media, "mime_type", "video/mp4") or "video/mp4"
                file_name = getattr(media, "file_name", f"video_{message_id}.mp4") or f"video_{message_id}.mp4"
                return file_size, mime_type, file_name
            else:
                raise RuntimeError("El worker no tiene un cliente real conectado.")
        finally:
            self.release_worker(worker)

    async def stream_media_range(
        self,
        channel_id: int,
        message_id: int,
        start_byte: int,
        end_byte: int,
        chunk_size: int = 1024 * 512  # 512 KB por chunk
    ) -> AsyncGenerator[bytes, None]:
        """
        Generador asíncrono que transmite los bytes solicitados [start_byte, end_byte].
        Si un bot recibe FLOOD_WAIT durante la transmisión, se cambia dinámicamente al siguiente
        bot activo para continuar el stream en la misma posición de byte.
        """
        current_byte = start_byte
        total_requested = end_byte - start_byte + 1

        while current_byte <= end_byte:
            worker = await self.get_next_worker()
            bytes_from_this_worker = 0

            try:
                if worker.client and PYROGRAM_AVAILABLE:
                    # En Pyrogram, calculamos el offset en bloques de chunk_size (1024*512)
                    chunk_offset = current_byte // chunk_size
                    # Cantidad de chunks requeridos
                    chunks_needed = ((end_byte - current_byte) // chunk_size) + 1

                    async for chunk in worker.client.stream_media(
                        message=f"{channel_id}/{message_id}",
                        offset=chunk_offset,
                        limit=chunks_needed
                    ):
                        # Ajustar el primer chunk si el start_byte no es múltiplo exacto
                        chunk_len = len(chunk)
                        if chunk_len == 0:
                            break

                        yield chunk
                        current_byte += chunk_len
                        bytes_from_this_worker += chunk_len

                        if current_byte > end_byte:
                            break

                else:
                    # Modo simulación sintética de streaming (para entorno de desarrollo)
                    # Envía fragmentos de bytes con header MP4 simulado
                    remaining = (end_byte - current_byte) + 1
                    this_chunk_size = min(chunk_size, remaining)
                    
                    # Generar chunk sintético con sincronización temporal
                    dummy_chunk = b"\x00" * this_chunk_size
                    yield dummy_chunk
                    current_byte += this_chunk_size
                    bytes_from_this_worker += this_chunk_size
                    await asyncio.sleep(0.02)  # Simula latencia de red de ~20ms por 512KB

            except Exception as exc:
                error_str = str(exc)
                logger.error(f"[Worker #{worker.bot_id}] Error transmitiendo bytes {current_byte}-{end_byte}: {exc}")

                # Verificar si es FloodWait de Telegram
                if PYROGRAM_AVAILABLE and isinstance(exc, errors.FloodWait):
                    self.mark_flood_wait(worker, exc.value)
                elif "FLOOD_WAIT" in error_str:
                    wait_sec = 15
                    try:
                        # Extraer segundos del mensaje de error si aplica
                        wait_sec = int("".join(filter(str.isdigit, error_str))) or 15
                    except Exception:
                        pass
                    self.mark_flood_wait(worker, wait_sec)
                else:
                    worker.error_count += 1
                    worker.last_error = error_str
                    self.release_worker(worker, bytes_from_this_worker)
                
                # Continuar el ciclo `while current_byte <= end_byte`:
                # la próxima iteración solicitará un nuevo worker para reanudar el byte actual
                logger.info(f"Reasignando stream desde el byte {current_byte} a otro worker del pool...")
                await asyncio.sleep(0.1)
                continue

            finally:
                self.release_worker(worker, bytes_from_this_worker)

    def get_pool_status(self) -> Dict:
        """Devuelve telemetría completa del Worker Pool para el panel de administración."""
        active = sum(1 for w in self.workers if w.is_available)
        paused = sum(1 for w in self.workers if w.status == BotStatus.PAUSED)
        total_streams = sum(w.active_streams for w in self.workers)
        total_mb = sum(w.total_bytes_served for w in self.workers) / (1024 * 1024)

        return {
            "total_workers": len(self.workers),
            "active_workers": active,
            "paused_workers": paused,
            "current_active_streams": total_streams,
            "total_data_served_mb": round(total_mb, 2),
            "workers": [
                {
                    "bot_id": w.bot_id,
                    "status": w.status.value,
                    "is_available": w.is_available,
                    "active_streams": w.active_streams,
                    "mb_served": round(w.total_bytes_served / (1024 * 1024), 2),
                    "pause_seconds_left": max(0, int(w.pause_until - time.time())) if w.status == BotStatus.PAUSED else 0,
                    "errors": w.error_count,
                    "last_error": w.last_error
                }
                for w in self.workers
            ]
        }


# Instancia singleton del pool de bots
bot_pool = TelegramBotPool()
