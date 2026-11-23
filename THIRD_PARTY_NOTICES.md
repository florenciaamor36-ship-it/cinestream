# Third-party notices

CineStream is distributed under the GNU General Public License, version 3.

## NewPipe Extractor

CineStream uses the NewPipe Extractor fork maintained by LibreTube (`com.github.libre-tube:NewPipeExtractor`, revision `3e863d7`) to discover publicly available YouTube stream metadata and language tracks. NewPipe Extractor is GPL-3.0-or-later. Source: https://github.com/Libre-tube/NewPipeExtractor .

CineStream also ports the SABR/UMP Media3 player implementation (including its manifest, media source, chunk source, data source, and protocol parser) from the user's `la-clave-videos` repository (https://github.com/florenciaamor36-ship-it/la-clave-videos), itself GPL-3.0. The app adapts its app-specific context and playback wiring while retaining the upstream protocol implementation. It uses NewPipe's optional PoToken provider hook but does not mint, reuse, or embed YouTube attestation tokens.

The complete corresponding source for this application is available in this repository. No paid API key or subscription is required by the NewPipe Extractor integration.
