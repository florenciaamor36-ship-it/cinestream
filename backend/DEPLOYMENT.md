# CineStream Backend - Guía de Despliegue en VPS y Mitigación de Bloqueos

Esta guía documenta la puesta en marcha en producción de la infraestructura backend de CineStream (FastAPI + Worker Pool de 20 Bots de Telegram) y las mejores prácticas para evitar bloqueos por IP o saturación de tasa (*FloodWait*) en Telegram.

---

## 1. Requisitos Previos en el VPS

- **Sistema Operativo:** Ubuntu 22.04 / 24.04 LTS o Debian 12.
- **Hardware Recomendado:**
  - Mínimo: 2 vCPU, 4 GB RAM (para manejar hasta 50 streams simultáneos).
  - Producción: 4-8 vCPU, 8-16 GB RAM con ancho de banda gigabit ilimitado.
- **Python:** 3.10 o 3.11 instalado.
- **Nginx:** Como reverse proxy y terminador SSL.

---

## 2. Instalación Paso a Paso

### 2.1 Clonar o copiar el directorio `backend`
```bash
sudo apt update && sudo apt install -y python3-pip python3-venv nginx git
cd /opt
sudo git clone <tu-repositorio> cinestream
cd /opt/cinestream/backend
```

### 2.2 Crear entorno virtual e instalar dependencias
```bash
python3 -m venv venv
source venv/bin/activate
pip install --upgrade pip
pip install -r requirements.txt
```

> **Nota:** La librería `tgcrypto` compila extensiones en C para acelerar el cifrado MTProto de Telegram hasta un 400%, reduciendo drásticamente el consumo de CPU del VPS.

### 2.3 Configurar variables de entorno (`.env`)
```bash
cp .env.example .env
nano .env
```
Ingresa tu `API_ID`, `API_HASH`, el ID de tu canal de Telegram (ej. `-100...`) y los tokens de los 20 bots (`BOT_TOKEN_1` al `BOT_TOKEN_20`).

---

## 3. Configuración del Servicio Systemd

Crea el archivo de servicio para mantener el servidor siempre activo:

```bash
sudo nano /etc/systemd/system/cinestream.service
```

Pega el siguiente contenido:
```ini
[Unit]
Description=CineStream FastAPI & Telegram Bot Pool Server
After=network.target

[Service]
User=root
WorkingDirectory=/opt/cinestream/backend
EnvironmentFile=/opt/cinestream/backend/.env
ExecStart=/opt/cinestream/backend/venv/bin/uvicorn main:app --host 127.0.0.1 --port 8000 --workers 1
Restart=always
RestartSec=5
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
```

Habilitar e iniciar:
```bash
sudo systemctl daemon-reload
sudo systemctl enable cinestream
sudo systemctl start cinestream
sudo systemctl status cinestream
```

---

## 4. Configuración Crítica de Nginx (HTTP Range Requests)

Para que ExoPlayer en Android pueda adelantar, retroceder y solicitar buffers sin demoras, Nginx **NO DEBE** acumular los fragmentos en buffer. Debe transmitir directamente los rangos HTTP (`206 Partial Content`):

Edita `/etc/nginx/sites-available/cinestream`:
```nginx
server {
    listen 80;
    server_name stream.tudominio.com;

    # Desactivar buffering para streaming continuo de video
    proxy_buffering off;
    proxy_request_buffering off;
    proxy_http_version 1.1;

    # Soporte crítico de Range Requests para Media3 / ExoPlayer
    proxy_force_ranges on;
    proxy_set_header Range $http_range;
    proxy_set_header If-Range $http_if_range;

    # Timeouts extendidos para conexiones de video persistentes
    proxy_connect_timeout 300s;
    proxy_send_timeout 600s;
    proxy_read_timeout 600s;

    location / {
        proxy_pass http://127.0.0.1:8000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # Headers CORS para aplicaciones móviles y clientes web
        add_header Access-Control-Allow-Origin * always;
        add_header Access-Control-Expose-Headers "Content-Range, Accept-Ranges, Content-Length" always;
    }
}
```

Habilitar el sitio y reiniciar Nginx:
```bash
sudo ln -s /etc/nginx/sites-available/cinestream /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl restart nginx
```

Instalar certificado SSL gratuito con Let's Encrypt:
```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d stream.tudominio.com
```

---

## 5. Estrategias de Arquitectura para Evitar Bloqueos de Telegram

Si la aplicación crece a miles de usuarios simultáneos, Telegram puede imponer restricciones de velocidad por IP (`FLOOD_WAIT`) o bloqueos temporales al centro de datos (DC). Aplica las siguientes medidas recomendadas:

### 1. Rotación de Proxies SOCKS5 / MTProto Distribuidos
En lugar de que los 20 bots salgan por la misma dirección IP del VPS:
- Asigna un pool de 4 a 5 proxies SOCKS5 dedicados (de centros de datos distintos o residenciales).
- En `bot_pool.py`, configura la propiedad `proxy` de Pyrogram por bot:
  ```python
  client = Client(
      name=f"bot_worker_{idx}",
      bot_token=token,
      proxy=dict(
          scheme="socks5",
          hostname="proxy.ip.address",
          port=1080,
          username="user",
          password="password"
      )
  )
  ```
- Al dividir 20 bots entre 5 proxies, cada dirección IP solo maneja 4 bots, manteniéndose muy por debajo de los umbrales de rate-limiting de Telegram.

### 2. Límite de Transmisiones Concurrentes por Bot
Configuramos `max_concurrent_transmissions=8` en Pyrogram. Esto impide que un único token abra cientos de hilos de descarga simultáneos en el mismo segundo.

### 3. Caché Local en Android (Implementado en la App)
La app Android utiliza `SimpleCache` con `LeastRecentlyUsedCacheEvictor` de Android Media3. Esto asegura que:
- Cuando el usuario retrocede 10 segundos, el segmento se sirve desde la memoria del teléfono.
- Si el usuario pausa la película, no se descargan bytes adicionales innecesarios.
- El tráfico global hacia el servidor se reduce entre un **30% y un 50%**.

### 4. Afinidad de Centro de Datos (DC Affinity)
Los archivos subidos a canales de Telegram se almacenan físicamente en un Data Center específico (ej. DC 2 - Ámsterdam, DC 4 - Países Bajos, DC 5 - Singapur). Procura contratar tu VPS en la misma región geográfica (ej. Europa / Frankfurt para DC 2/4) para reducir la latencia de MTProto de 120ms a menos de 10ms.
