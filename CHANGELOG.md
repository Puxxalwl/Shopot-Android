# Changelog — Shopot Android

All notable changes to the Android client. Format is based on [Keep a Changelog](https://keepachangelog.com/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- Initial project skeleton: Gradle multi-module structure, AGENTS.md
- Binary protocol codec for CORE-API 2.2.0 (`data` KMP module): Frame header
  parsing, all 32 request methods, 12 event types, 14 status codes
- Glassmorphism style: BlurGlassSurface composable with RenderEffect
  (API 31+) and RenderScript fallback (API 26–30)
- Bottom navigation with 4 tabs: Profile, Chats, Contacts, Settings
- Foreground service architecture for shopot-core daemon lifecycle
- Cross-compilation tooling for shopot-core as `aarch64-linux-musl`

### Changed
- Declared the Android 14+ `dataSync` foreground-service permission and removed the unused `connectedDevice` service type, preventing a startup `SecurityException`.
- Added `core-src/` as a pinned git submodule for shopot-core.
- Replaced boxed byte accumulation in the protocol encoder with a reusable
  primitive `ByteArray` buffer and geometric growth.
- Serialized UDS request writes and moved response reads into one event-reader
  path to avoid concurrent stream reads.
- Raw avatar payloads no longer pass through UTF-8 `String` conversion.
- Added generated CORE-API objects and deterministic SVG screenshot examples.
- Added the supplied Shopot logo to Android resources.
- Documented the Linux reference client `EryyynIT/shopot`.

### Security
- No third-party services, telemetry, or analytics SDKs
- Client caches are memory-only
- Protocol bytes verified against CORE-API.md §2
- Original Android client code is AGPL-3.0-or-later with attribution to
  `puxxalwl` and `EryyynIT`; the core submodule retains its upstream license.
