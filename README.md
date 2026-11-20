# CineStream

CineStream is an Android client and a FastAPI backend for browsing and playing media from sources configured by the owner. The intended first milestone is a **private personal library**: an Android app talks to the owner's backend; the backend reads an owner-controlled Telegram channel and streams only media the owner is authorized to use.

## Current state

- Android: Compose catalog/detail screens, Media3 player, server URL setting, and IPTV/EPG UI are present.
- Backend: FastAPI catalog, Telegram channel scanner, HTTP byte-range streaming, IPTV import and EPG endpoints are present.
- Not yet production-ready: Telegram access and byte-range playback need end-to-end verification; Android builds need to be restored to reproducible CI; server authentication, HTTPS, and deployment need to be configured before exposing it to a network.
- Do not treat the current repository as a ready-to-install APK. No sample or demo catalog is evidence that Telegram playback works.

## First milestone

1. Keep the Telegram channel private and use only content you have permission to store and play.
2. Add the bot `@CineStreamLucasBot` as a channel administrator with only the access it needs. Start with one bot for a small private test. Never commit bot tokens, API credentials, TMDb keys, or admin keys.
3. Configure the backend on a private server with HTTPS and a strong `ADMIN_API_KEY`.
4. Run the authenticated scan and confirm a video appears in the catalog.
5. Verify normal playback, seeking, resume, invalid ranges, disconnects, and server restart on a real Android device.
6. Only after that, decide whether to add series metadata, artwork correction, favorites, or any separate live-TV features.

A bot token is a server-side secret and must never be embedded in the APK. The scan endpoint is admin-only (`X-Admin-Key`) and must not be exposed without TLS. Use only a Telegram source the account and bot are authorized to access.

## Backend development

```sh
cd backend
python -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
# Edit .env locally; do not commit it.
uvicorn main:app --host 127.0.0.1 --port 8000
```

Run the unit tests from the repository root:

```sh
PYTHONPATH=backend python -m unittest discover -s backend/tests -v
```

## Android development

Open the repository in Android Studio, let it install the project's Gradle/Android toolchain, then build and test on an emulator and a physical device. The repository currently lacks the Gradle wrapper scripts and wrapper JAR, so command-line/CI builds are not yet reproducible. The backend loads `backend/.env` at startup for local development; deployment environments may instead inject variables directly.
