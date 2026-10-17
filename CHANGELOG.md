# Changelog — Stille-Messenger

All notable changes to the Android client. Format is based on [Keep a Changelog](https://keepachangelog.com/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- Added a short animated motion-blur transition to the Welcome screen when setup is completed; other windows remain unchanged.
- Added live camera QR scanning for invitations with runtime permission handling, bounded in-memory decoding and immediate camera release after a valid code. Image import remains available as a fallback.
- ACCEPT_INVITE now tracks the returned cid as pending until authenticated presence/JOIN, refreshes the bounded contact snapshot when the peer becomes online, and keeps simultaneous one-time invite races deterministic without trusting unauthenticated names.
- Reduced tab, segmented-control and conversation transitions to 180–200ms, removed whole-page GPU blur and welcome-submit delay, and changed theme switching to animated semantic colors instead of an opaque full-screen light/dark flash.
- Added a short, cheap theme crossfade that keeps the previous background over the new theme instead of repainting the whole screen with a dark/light overlay; removed the expensive welcome-screen blur to reduce transition jank.
- Added Wi-Fi multicast permission/lock handling so address-less invitations can discover peers more reliably on Android LANs.
- Added a theme-switch transition that expands a softly blurred square-like bubble into the new light or dark background, plus a lazy sliding square indicator for segmented choices.
- Replaced launcher resources with the supplied minimal Stille Android icon package: correctly named `ic_launcher.png` and `ic_launcher_round.png` variants for ldpi through xxxhdpi, plus a 512px master source. Removed the old transparent adaptive foreground so installed-app icons use the supplied artwork.
- Profile photo selection with off-thread, bounded EXIF-aware center cropping and JPEG encoding (8 MiB source, ≤16,000-byte upload); SET_AVATAR acknowledgement gates success and errors keep the previous photo. No new dependencies or plaintext disk cache.
- Memory-only avatar display in Profile, Chats, Contacts and conversation headers, capped at 20 images including own photo. Peer images require confirmed JOIN, matching content id and bounded decoded dimensions. Own preview cannot be restored after process death with the current core API; see README.
- Invitations through the existing binary CREATE_INVITE/ACCEPT_INVITE methods, with a bound-service state flow, explicit offline/error/waiting states, one-time code reuse, copy/share actions and QR image import. New contact identity is shown only after CONTACT_JOINED.
- Branded black-on-white QR with a symbol-only monochrome mask derived from the original Stille logo. Each decorated raster is decoded before display; a verified undecorated code is the fallback. Added ZXing core 3.5.3 solely for offline QR encode/decode, with no networking SDK or cryptography changes.
- First-run welcome setup with language, theme, and display-name selection
- Initial project skeleton: Gradle multi-module structure, AGENTS.md
- Binary protocol codec for CORE-API 2.2.0 (`data` KMP module): Frame header
  parsing, all 32 request methods, 12 event types, 14 status codes
- Glassmorphism style: BlurGlassSurface composable with RenderEffect
  (API 31+) and RenderScript fallback (API 26–30)
- Bottom navigation with 4 tabs: Profile, Chats, Contacts, Settings
- Foreground service architecture for shopot-core daemon lifecycle
- Cross-compilation tooling for shopot-core as `aarch64-linux-musl`

### Changed
- Replaced abrupt main-tab crossfades with swipeable sections, synchronized bottom navigation and 360ms eased tap transitions. Added bounded directional motion-blur approximation on Android 12+ (sharp at rest), slide/scale/fade fallback on older Android, and animated conversation entry/back to the source section.
- Fixed local-core packaging and startup: package the AArch64 executable as `libshopot.so`, extract via native packaging and execute directly from `nativeLibraryDir` rather than writable app data (blocked on Android 10+). APK packaging rejects a missing/wrong-architecture core.
- Initialize a missing core identity once before `serve`, preserve existing/malformed seeds, bound and cancel init, discard process output, and log only fixed startup stages/error types. Restore START_STICKY launches and add retry actions to invitations/profile; no protocol or encryption changes.
- Added softly blended blue/lilac background gradients, translucent highlighted surfaces, gradient avatars and a rounded floating navigation capsule with short selection feedback. These are lightweight glass-inspired effects, not a simulated full-screen blur/refraction engine.
- Redesigned welcome, chats, contacts, conversation, profile, settings and tab navigation with neutral surfaces, inset list separators, semantic light/dark colors, a single blue accent and consistent typography. See [UI direction and research](docs/UI-DESIGN.md) ([Russian](ru/UI-DESIGN.md)).
- Added working local name search, an explicit profile Save action, reactive language/theme values and a system appearance option in settings. Removed inert call/attachment/menu controls.
- Shortened tab transitions to 180ms without delaying callbacks, cached list preparation, bounded conversation rendering to 200 messages, kept the keyboard open after sending and moved profile image loading to Coil without disk caching. These changes have not been device-benchmarked.
- Refreshed the main chats screen with a theme-aware gradient background, rounded translucent panels, floating navigation and a first-conversation empty state. Glass surfaces now honor their background color and clip content to their rounded shape.
- Improved welcome contrast with controlled backgrounds, enlarged the transparent logo, removed the name-field icon and technical counter wording, and added compact single-choice controls and a bilingual “Why Stille?” explanation.
- Used the background logo exclusively for launcher icons.
- Rebranded the Android project as `stille` / Stille Messenger with a transparent logo and updated launcher icons; changed the application ID to `com.stille.messenger` (a separate installation without automatic data migration).
- Anchored the welcome Continue button below the scrollable form, with keyboard insets.
- Limited profile names to 1–32 UTF-8 bytes as required by core SET_NAME, with a byte counter and Unicode boundary tests.
- Fixed release publishing by using a dedicated `RELEASE_TOKEN` secret with repository Contents write permission when the default GitHub Actions token is restricted.
- Declared the Android 14+ `dataSync` foreground-service permission and removed the unused `connectedDevice` service type, preventing a startup `SecurityException`.
- Added `core-src/` as a pinned git submodule for shopot-core.
- Replaced boxed byte accumulation in the protocol encoder with a reusable
  primitive `ByteArray` buffer and geometric growth.
- Serialized UDS request writes and moved response reads into one event-reader
  path to avoid concurrent stream reads.
- Raw avatar payloads no longer pass through UTF-8 `String` conversion.
- Added generated CORE-API objects and deterministic SVG screenshot examples.
- Added the supplied Stille logo to Android resources.
- Documented the Linux reference client `EryyynIT/shopot`.

### Security
- No third-party services, telemetry, or analytics SDKs
- Client caches are memory-only
- Protocol bytes verified against CORE-API.md §2
- Original Android client code is AGPL-3.0-or-later with attribution to
  `puxxalwl` and `EryyynIT`; the core submodule retains its upstream license.
