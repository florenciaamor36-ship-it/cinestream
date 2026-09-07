"""
CineStream Database Layer
Manejo de modelos SQLAlchemy asíncronos y conexión a SQLite / PostgreSQL.
"""

from datetime import datetime
from typing import Optional, List
import os
from sqlalchemy import Column, Integer, String, Text, Float, BigInteger, Boolean, DateTime, select
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession, async_sessionmaker
from sqlalchemy.orm import declarative_base

DATABASE_URL = os.getenv("DATABASE_URL", "sqlite+aiosqlite:///./cinestream.db")

engine = create_async_engine(
    DATABASE_URL,
    echo=False,
    connect_args={"check_same_thread": False} if "sqlite" in DATABASE_URL else {}
)

AsyncSessionLocal = async_sessionmaker(
    bind=engine,
    class_=AsyncSession,
    expire_on_commit=False
)

Base = declarative_base()


class Pelicula(Base):
    """
    Modelo de película o episodio almacenado en Telegram y enriquecido con TMDb.
    """
    __tablename__ = "peliculas"

    id_pelicula = Column(Integer, primary_key=True, autoincrement=True, index=True)
    titulo = Column(String(255), nullable=False, index=True)
    sinopsis = Column(Text, nullable=True)
    poster_url = Column(String(512), nullable=True)
    backdrop_url = Column(String(512), nullable=True)
    genero = Column(String(100), nullable=False, index=True, default="Acción")
    
    # Referencia al archivo en Telegram (Oculto para el cliente final)
    canal_telegram_id = Column(BigInteger, nullable=False, index=True)
    message_id_telegram = Column(Integer, nullable=False, index=True)
    file_id_telegram = Column(String(255), nullable=True)
    
    duracion_min = Column(Integer, nullable=True, default=120)
    anio_estreno = Column(Integer, nullable=True, default=2024)
    calificacion = Column(Float, nullable=True, default=8.5)
    tamano_bytes = Column(BigInteger, nullable=True)
    calidad_video = Column(String(50), nullable=True, default="1080p")
    mime_type = Column(String(50), nullable=True, default="video/mp4")
    es_destacada = Column(Boolean, default=False)
    fecha_agregada = Column(DateTime, default=datetime.utcnow)

    def to_dict(self, base_stream_url: str = "") -> dict:
        return {
            "id_pelicula": self.id_pelicula,
            "titulo": self.titulo,
            "sinopsis": self.sinopsis or "",
            "poster_url": self.poster_url or "",
            "backdrop_url": self.backdrop_url or "",
            "genero": self.genero,
            "duracion_min": self.duracion_min or 0,
            "anio_estreno": self.anio_estreno or 2024,
            "calificacion": self.calificacion or 0.0,
            "calidad_video": self.calidad_video or "1080p",
            "tamano_bytes": self.tamano_bytes or 0,
            "es_destacada": bool(self.es_destacada),
            # El endpoint público oculta por completo el canal y el token de Telegram:
            "stream_url": f"{base_stream_url}/stream/{self.id_pelicula}"
        }


class ListaM3U(Base):
    __tablename__ = "listas_m3u"

    id = Column(Integer, primary_key=True, autoincrement=True)
    nombre = Column(String(255), nullable=False)
    url_fuente = Column(String(1024), nullable=True)
    activa = Column(Boolean, nullable=False, default=True)
    ultima_actualizacion = Column(DateTime, nullable=True)


class CanalM3U(Base):
    __tablename__ = "canales_m3u"

    id = Column(Integer, primary_key=True, autoincrement=True)
    lista_id = Column(Integer, nullable=False, index=True)
    nombre = Column(String(255), nullable=False, index=True)
    url = Column(String(2048), nullable=False)
    grupo = Column(String(255), nullable=False, default="Sin categoría", index=True)
    logo_url = Column(String(1024), nullable=True)
    tvg_id = Column(String(255), nullable=True)
    activo = Column(Boolean, nullable=False, default=True)
    ultima_verificacion = Column(DateTime, nullable=True)


async def init_db():
    """Crea las tablas en la base de datos si no existen."""
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)


async def get_db_session() -> AsyncSession:
    async with AsyncSessionLocal() as session:
        yield session
