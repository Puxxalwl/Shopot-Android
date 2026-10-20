# Changelog — Stille-Messenger

All notable changes to the Android client. Format is based on [Keep a Changelog](https://keepachangelog.com/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Changed
- Documentation restructured. `README.md` is a compact entry point (logo
  header, badges, docs index); user guide, build/release and architecture
  material moved to `docs/USAGE.md`, `docs/BUILD.md` and
  `docs/ARCHITECTURE.md` with RU mirrors. Generated screenshots and their
  generator were removed; the stille.im owl mascot and a logo header were
  added under `docs/assets/`. Stale `shopot-core` references, broken
  `CORE-API.md` links and outdated feature claims (file transfer, voice
  calls are core-only) were corrected across all documents.

## [3.4.0]

### Added
- Chat bubbles render UTF-8 byte-based formatting entities safely (including
  links) and offer emoji reactions backed by the core reaction API.
- The official Stille owl mascot is included in the empty chats state as a local drawable.

### Fixed
- Reactions compile errors (experimental `combinedClickable` opt-in, lambda
  arities) and an entity-formatting test whose expectation contradicted the
  UTF-8 offset contract: a range covering a whole emoji is valid, not
  malformed.

## [3.3.0]

### Added
- **Permanent invite link**: the invitation sheet now also shows
  `https://stille.im/invite/{code}` — the persistent profile code
  (`CREATE_INVITE_EX` mode 4: deterministic, never burns, no invite-registry
  limit) with the `stille://` scheme stripped. The link carries the complete
  invitation itself, so anyone who receives it connects directly; the
  stille.im site is only a redirector. **Reset link** rotates the profile
  code (`REGENERATE_PROFILE_CODE`): a leaked link stops validating
  immediately, existing contacts are unaffected.
- Deeplink handling: `https://stille.im/invite/i/…` and `stille.im/invite/i3/…`
  links (with or without the `https://` prefix; plain `http://` rejected), and
  direct `stille://i…` / `stille://i3…` codes, open the app (`ACTION_VIEW`,
  `singleTask` MainActivity) and route into the accept flow. The client
  reconstructs the code locally by re-attaching the `stille://` scheme — no
  server-side state or round-trip. The camera scanner, QR image import and
  pasted input accept all of these through the same parsing.
- Codec support for API 2.8.0: methods 52–54 (`GENERATE_SHORT_CODE`,
  `DECODE_SHORT_CODE`, `REGENERATE_PROFILE_CODE`), `CREATE_INVITE_EX` mode 4
  and client-side Crockford short-code normalization (mirroring core
  `invite.zig`, including the I/L→1, O→0, U→V typo repairs and the positional
  checksum), with unit tests against the core golden vector `N9S5WMD9D`.

### Changed
- Core submodule updated to API 2.8.0 (event-driven LAN discovery and the
  `stille-messenger-core` rename included). The generated
  `GeneratedCoreApi.kt` now pins 54 methods and the 2.8.0 version string.
- CI (core): the `build (macos-14, aarch64-macos, native)` push matrix entry
  was removed from the core repository's `ci` workflow — a separate macOS
  sub-core will own that target.

## [3.2.0]

### Changed
- Core submodule updated to the `stille-messenger-core` rename: the data
  directory moved from `~/.shopot` to `~/.stille-messenger` (the core
  migrates it atomically on first start — existing installs keep their
  identity, contacts and history), the UDS socket is now
  `~/.stille-messenger/api.sock`, and new invites use the canonical
  `stille://i/…` / `stille://i3/…` codes. The client follows all three
  changes and still accepts legacy `shopot://` and `shopot1.` codes, both
  scanned and pasted.
- Event-driven LAN detect and immediate dial from the updated core: a
  message to an offline peer now triggers a dial job plus a burst
  discovery announcement right away instead of waiting for background
  cycles (no client protocol change; multicast lock behavior unchanged).

### Fixed
- CI: test function names containing dots failed Kotlin compilation once
  the build cache missed; renamed to keep `:data:testDebugUnitTest`
  compiling.
- The QR scanner now also accepts `shopot://i3/` hybrid codes (it
  previously matched only `shopot://i/` and `shopot1.`).

### Added
- Recovery backup storage providers beyond Google Drive, with no new
  dependencies and no project-operated servers: **Yandex Disk**
  (`webdav.yandex.ru`, app password) and **any WebDAV server** (Nextcloud,
  ownCloud, …) over HTTPS with Basic auth. WebDAV passwords are stored
  encrypted at rest (AES-256-GCM, Android Keystore). The provider is chosen
  when enabling recovery and again when restoring on a new device.
- Redacted file-backed diagnostics log (`files/logs/stille.log`, bounded to
  512 KiB with one rotated generation; fixed stages and exception class names
  only — never messages, contacts, invite codes or keys). Open it from
  Settings by tapping "About Stille" five times quickly, then share or clear
  it; uncaught exceptions are recorded there too.
- Live QR invitation scanner: the camera permission is now requested as
  soon as the scanner opens instead of showing a black screen; `Camera.open()`
  and preview configuration moved off the main thread onto a dedicated
  worker with one automatic retry; the overlay insets itself from
  `safeDrawing` so the frame and buttons align on all devices.
- Google Drive recovery consent is now launched interactively: when the
  account manager returns a consent intent, the client opens Google's
  screen and retries the interrupted enable/restore/delete automatically.
- In-app updates install themselves: the "install unknown apps" permission
  is requested *before* the APK download starts (previously the whole
  50 MiB was downloaded and only then failed), the download resumes
  automatically when the user returns from the permission screen, and
  Android's install confirmation opens by itself once the verified
  download is ready.

## [3.1.1]

### Fixed
- Match release APK assets using the published `Stille-Messenger-v*.apk` filename, including the `v` prefix from the release tag.

## [3.1.0]

### Fixed
- Wire the conversation composer to `SEND_MESSAGE`, add optimistic rendering and server-ID reconciliation, and retain the draft when the request fails.
- Show only contacts with message history in Chats.
- Add long-press actions to pin/unpin, delete, and block/unblock a chat.
- Fix device-link LAN discovery returning before the holder starts the authenticated connection.

## [3.0.1]

### Added
- Added optional once-per-launch GitHub Releases checks, an update badge and an update sheet, plus verified in-app APK downloads that hand off to Android's install confirmation. Users can disable automatic checks while retaining manual checks in About Stille.
- Updated the core submodule to API 2.7.0. Friend invitations now use the
  one-key `ACCEPT_INVITE_EX` flow; authenticated JOIN completion, rather than
  a second code or manual confirmation, establishes the contact.
- Contact synchronization uses `SUBSCRIBE_CONTACT_UPDATES`, `GET_BOOTSTRAP`,
  revision deltas, and contact update events to avoid repeated full-list
  requests. Extended invite creation uses reachable LAN endpoints when safe.


## [0.2.6]

### Fixed
- Bump APK metadata to `0.2.6`/versionCode `26` and verify the renamed `libstille.so` in release packaging.

- Recovery enrollment now offers the recovery-code path when passkey creation or PRF is unavailable, while respecting cancellation. Google Drive token requests use the required `oauth2:` prefix and can show interactive consent. Recovery UI callbacks run on the main thread, network/token I/O stays on IO, and the activity owns the operation scope. Added regression tests for passkey fallback and cancellation.
- Removed full-page GPU blur from section swipes, prepared direct pager neighbours and moved segmented-indicator animation to placement. Theme changes install semantic colors once instead of recomposing all screens for fifteen animated colors; the wallpaper blends in its draw phase. On-device frame timing remains to be measured.

### Changed
- Dark appearance now uses a Midnight navy palette. Rounded glass cards use a 40% translucent fill and a soft gradient rim without expensive blur or shadow layers; segmented controls have rounded indicators and eased 260ms motion, tab taps use 300ms transitions.
- Reworded Russian and English setup, profile, invitation, settings and recovery copy using concise everyday language, with actionable recovery errors and an explicit reminder that message history is not restored.

### Added
- Account recovery backed by core API 2.6.0: the core seals the recovery package (`CREATE_RECOVERY_BACKUP`/`RESTORE_RECOVERY_BACKUP`, §4.10) with ChaCha20-Poly1305 and owns the format; the client only runs the unlock ceremony and stores the sealed blob plus a public metadata sidecar in the user's Google Drive `appDataFolder` (two files: `stille-recovery-v1.bin` / `.meta`). No message history ever leaves the device. New-device restore boots the daemon unprovisioned (serve without a seed — `StilleService.startRecovery`), applies the blob, and the profile (name, avatar, contacts, routes) plus networking come back with an intentionally empty history; cancelling stops the unprovisioned daemon. Unlock secret: WebAuthn `prf` extension of a Google-synced passkey (32-byte salt in the sidecar) with a mandatory random recovery-code fallback shown once. Own avatar after restart now loads via `GET_MY_AVATAR` (40). New statuses `BAD_RECOVERY`/`NOT_INITIALIZED`/`ALREADY_PROVISIONED` and events `REPLY_RECEIVED`/`CONFIG_CHANGED` are wired into the codec; the recovery blob and both Drive files are covered by new JVM tests. `androidx.credentials` (1.5.0) is the only new dependency, used solely for the passkey ceremony. See [docs/RECOVERY.md](docs/RECOVERY.md) ([Russian](ru/RECOVERY.md)).
- Core submodule switched to `stille-messenger-core` (master `3424e1f`, API 2.6.0): cross-compiled as `libstille.so` (replaces `libshopot.so`); `tools/build-core.sh` tracks the new repo layout.

### Fixed
- Set the next APK version to `0.2.5`/versionCode `25` instead of the stale `0.1.0`/`1` metadata. This does not bypass Android's signer compatibility requirement.
- Release APKs no longer fall back to ephemeral debug signing keys. Require a persistent keystore and verify the pinned signer fingerprint, ZIP integrity and alignment before publishing. Confirmed v0.2.3/v0.2.4 are individually valid but have different signing certificates; updating old installations still requires their original private key.
- Restore cached directional GPU blur from v0.2.2 on Android 12+, retaining the faster 200ms tab transitions and reduced-motion fallback.

### Added
- Protected local release-build helper reuses the permanent keystore outside Git, refuses missing keys or unsafe permissions, and disables signing configuration caching. Four stdlib tests cover these safeguards.
- Added a short animated motion-blur transition to the Welcome screen when setup is completed; other windows remain unchanged.
- Added live camera QR scanning for invitations with runtime permission handling, bounded in-memory decoding and immediate camera release after a valid code. Image import remains available as a fallback.
- ACCEPT_INVITE now tracks the returned cid as pending until authenticated presence/JOIN, refreshes the bounded contact snapshot when the peer becomes online, and keeps simultaneous one-time invite races deterministic without trusting unauthenticated names.
- Reduced tab, segmented-control and conversation transitions to 180–200ms, restored cached directional GPU blur on Android 12+ with a reduced-motion fallback, removed the welcome-submit delay, and changed theme switching to animated semantic colors instead of an opaque full-screen light/dark flash.
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
