# Stille Messenger (Android)

**English** | [Русский](ru/README.md)

Serverless P2P messenger Android client for the [stille-messenger-core](https://github.com/puxxalwl/stille-messenger-core) daemon.

## Features

- **P2P — no servers.** Direct TCP (ports 7777–7786), LAN discovery, or
  self-hosted relay (IP hiding).
- **End-to-end encrypted.** The client never touches keys — the `shopot serve`
  daemon owns all cryptography (double ratchet, AEAD, forward secrecy).
- **No telemetry, no analytics, no Firebase.**
- **Glassmorphism UI** — iPhone-style translucent panels with blur.
- **Rich messages** — bold, italic, strikethrough, spoiler, code, links.
- **Reactions** — any Unicode emoji.
- **Files & photos** — up to 64 MB, resumable.
- **Voice calls** — 16 kHz E2EE with ADPCM+DTX.
- **Offline delivery** — encrypted outbox with ACKs and dedup.
- **Account recovery** — a passkey + one core-sealed file in your own
  cloud (Google Drive, Yandex Disk or any WebDAV server) restores your
  identity, profile, contacts and routes on a new device; message history
  is never synced. See [docs/RECOVERY.md](docs/RECOVERY.md).

## Screenshots

Generated examples are in [docs/screenshots](docs/screenshots):

![Welcome](docs/screenshots/welcome.svg)
![Chats](docs/screenshots/chats.svg)
![Chat](docs/screenshots/chat.svg)
![Settings](docs/screenshots/settings.svg)

Regenerate them with `python3 tools/generate_screenshots.py`. The supplied
Stille logo is stored at `app/src/main/res/drawable-nodpi/stille_logo.png`.
Installed-app launcher icons use the supplied minimal density set in
`app/src/main/res/mipmap-*`, with `ic_launcher.png` and `ic_launcher_round.png`
names. The 512px source is retained at
`app/src/main/res/drawable-nodpi/stille_launcher_master.png`; this is not a Play
Market asset set.

## First launch

On the first launch, Stille Messenger opens a local welcome setup where you choose the
interface language, light/system/dark theme, and the name shown on your profile.
These choices are stored locally on the device and can be changed later in the
app settings/profile. Stille can check the public GitHub Releases feed once per
launch; this can be disabled in Settings while manual checks remain available in
About Stille. Updates are downloaded only after confirmation and are verified
(checksum, signing certificate, version) before Android's system installer
opens automatically — the missing "install unknown apps" permission is
requested before the download starts, not after it.


Stille means “silence” in German. Names are limited to 1–32 UTF-8 bytes, matching
the core (16 Cyrillic letters or 8 four-byte emoji). Continue stays at the bottom
while the form scrolls. The Android application ID is now `com.stille.messenger`; this is a separate installation without automatic migration of old data.

## Navigation

Swipe horizontally between Profile, Chats, Contacts and Settings, or use the
bottom tabs. Transitions slide smoothly with a subtle motion-blur approximation
on Android 12+; older devices use slide/scale/fade without blur. Content is sharp
at rest. Swiping sections is disabled inside conversations and invitation sheets.
Back from a conversation returns to the section you opened it from.

## Profile photo

In Profile, choose **Set photo** / **Change photo**. The system picker needs no
broad storage permission. Images up to 8 MiB are processed off-thread: EXIF
orientation, center square crop, JPEG without metadata, and resizing/compression
to the core limit of 16,000 bytes. Success is shown only after SET_AVATAR confirms
the content id and size. Failures keep the previous photo.

Profile, chats and contacts render memory-only images with initials as fallback.
The cache holds at most 20 images (including your own); peer images require a
confirmed JOIN. The core stores the uploaded photo encrypted across restarts.
**Current API limitation:** it has no method to read your own photo bytes back,
so your own preview is available only while the service process retains it.
Peer previews depend on AVATAR_RECEIVED; this client does not poll or write a
plaintext image cache. No camera capture, manual crop editor or remove-photo API
is implemented.

## Invitations and live QR scanning

Open **Add contact** from Chats or Contacts. The invitation holder displays a
one-time QR/link; the other device can use **Scan with camera** for in-memory,
continuous QR decoding or choose a saved image. Camera frames are bounded to a
single decoder worker, never logged or written to disk, and the camera is
released when the scanner closes or the app pauses. Invite acceptance tracks the
core-returned contact id as pending until authenticated presence/JOIN confirms
it, so two devices using one code do not make the UI trust a provisional name.
Theme colors now crossfade semantically; tab and conversation transitions are
short (200 ms for tabs), with the cached GPU motion blur restored from v0.2.2
on Android 12+; older devices retain slide/scale/fade. After `ACCEPT_INVITE`, the
UI waits for the authenticated `CONTACT_JOINED` event; simultaneous acceptance
of one one-time code is serialized by the core and only the first valid JOIN
can create the connection. Both devices must be on the same LAN for an
address-less invite, with multicast enabled, or use a reachable address/relay.

## Setup

### Prerequisites
- Android 8.0+ (API 26)
- JDK 17
- [Zig 0.17.0](https://ziglang.org/download/) (for building stille-messenger-core)

### Build

```bash
# 1. Build stille-messenger-core binary for Android arm64-v8a
bash ./tools/build-core.sh

# 2. Build the Android app
./gradlew :app:assembleDebug

# 3. (optional) Run tests
./gradlew :data:testDebugUnitTest :app:testDebugUnitTest

# Fast incremental Kotlin check (keeps Gradle/Kotlin daemons warm)
./gradlew :app:compileDebugKotlin --daemon --offline --max-workers=2
```

### Release signing and installation

Release builds (current version `3.1.1`, versionCode `30`) require a persistent private keystore via `STILLE_KEYSTORE_PATH`,
`STILLE_STORE_PASSWORD`, `STILLE_KEY_ALIAS`, and `STILLE_KEY_PASSWORD`. Debug
builds remain independent. Never commit a keystore or paste its contents into logs.

GitHub Actions requires secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_STORE_PASSWORD`,
`ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, plus repository variable
`ANDROID_SIGNING_CERT_SHA256` (public certificate SHA-256, 64 hex characters).
The workflow verifies the APK signature, pinned certificate, ZIP integrity and
alignment before publication. Missing configuration stops a release; it never
falls back to a newly generated debug key.

Published v0.2.3 and v0.2.4 used different ephemeral runner keys: both APKs verify,
but Android cannot install one over the other. Updating an existing installation
requires its original private signing key; the APK contains only the public
certificate. Do not uninstall or clear app data to troubleshoot: identity and
history may be lost. A new permanent key fixes future continuity, not old installs.

For the protected local key layout (`~/.local/share/stille-signing/release.p12`
and `release.password`, alias `stille-release`), run:

```bash
python3 tools/build-signed-release.py --offline
```

Keep the directory private (700), files private (600), and a secure backup of
both outside this repository. The helper never regenerates a missing key and
turns off Gradle configuration caching for signing. Local builds use one worker,
a 1536 MiB Gradle heap and in-process Kotlin compilation to avoid two oversized JVMs.
Git SSH access alone cannot
configure GitHub Actions secrets; that requires repository Secrets write access.

### Architecture

```
app/                      # Android app: Compose UI + foreground service
├── ui/                   # Compose screens, components, theme
├── service/              # StilleService (foreground) + BootReceiver
data/                     # Protocol codec (matches CORE-API.md byte-for-byte)
tools/                    # Cross-compile scripts for stille-messenger-core
core-src/                 # stille-messenger-core source (git submodule / cloned)
```

The `data` module implements the binary UDS protocol codec. The
`StilleService` foreground service:
1. Uses `libstille.so` from Android's installed `nativeLibraryDir` (an executable, not JNI)
2. Creates the local core identity once with `stille-messenger init`, never overwriting an existing seed (or starts unprovisioned in recovery mode)
3. Starts `stille-messenger serve` and connects to `~/.shopot/api.sock` (UDS)
4. Sends `GET_VERSION` to verify core compatibility
5. Listens for events and broadcasts them to the UI via `StateFlow`

If invites or profile photo actions say that the local core is unavailable,
rebuild the APK after `bash ./tools/build-core.sh`. The APK must contain
`lib/arm64-v8a/libstille.so`, generated from
`app/src/main/jniLibs/arm64-v8a/libstille.so` (ignored by Git).
Packaging enables native extraction: Android 10+ blocks execution from writable
app data, so an asset copied into `filesDir` cannot be used. APK packaging fails
if the AArch64 core is missing; Kotlin-only checks need no binary. Profile and
invite screens offer a retry action. Startup logs contain only fixed stages and
exception types, never core output or identities. A redacted diagnostics log
file (stages, error types; no messages, contacts or secrets) can be shared
from Settings by tapping "About Stille" five times quickly. Install the updated APK over
the existing app; do not clear data to troubleshoot startup.

### Adding a friend

Open the invite action from Chats or Contacts. Stille creates a one-time invite
through the binary `CREATE_INVITE` API and keeps it in memory only. You can share
the link, copy it, or show a black-and-white QR with the symbol-only Stille mark
(no wordmark). The recipient can paste the link or choose a QR image; Stille
accepts it through `ACCEPT_INVITE` and shows the contact only after the trusted
`CONTACT_JOINED` event.

```
A: CREATE_INVITE → "stille://i/ABC123..."
   (share over any messenger or QR)
B: ACCEPT_INVITE → wait for CONTACT_JOINED → connected
```

## Documentation

| Document | Content |
|---|---|
| [AGENTS.md](AGENTS.md) | Contributor contract for the Android client |
| [CONTRIBUTING.md](CONTRIBUTING.md) | How to contribute |
| [DOCS.md](DOCS.md) | Documentation writing rules |
| [SECURITY.md](SECURITY.md) | Security policy |
| [CHANGELOG.md](CHANGELOG.md) | Changelog |

For the core API specification, see:
[`core-src/zig/docs/CORE-API.md`](core-src/zig/docs/CORE-API.md) or the
upstream [stille-messenger-core](https://github.com/puxxalwl/stille-messenger-core).

## Linux client

The official Linux desktop client is [EryyynIT/shopot](https://github.com/EryyynIT/shopot).
It is built with Rust and iced, uses the same core daemon and binary
UDS API, and is the reference desktop client for cross-platform UX and
integration behavior. This Android project shares the core contract but does
not copy Linux client source code.

## License

The original Android client code is licensed under **AGPL-3.0-or-later**.
All forks, modified versions, and redistributions of covered work must use the
same license and retain attribution to `puxxalwl` and `EryyynIT`. See
[LICENSE](LICENSE), [LICENSE-AGPL-3.0.txt](LICENSE-AGPL-3.0.txt), and
[NOTICE](NOTICE).

The `core-src/` submodule is an independent stille-messenger-core project and retains
its upstream MIT license. The Linux client [EryyynIT/shopot](https://github.com/EryyynIT/shopot)
is referenced for UX/integration and is not copied into this repository.
