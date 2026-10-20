# Building Stille

**English** | [Русский](../ru/BUILD.md)

Everything needed to build, test and publish the APK. For what the client
does at runtime, see [ARCHITECTURE.md](ARCHITECTURE.md).

## Prerequisites

| Tool | Version | Needed for |
|---|---|---|
| JDK | 17 | all builds |
| Android SDK | API 35 target, minSdk 26 | the app |
| [Zig](https://ziglang.org/download/) | 0.17.0 | cross-compiling the core |

Output is an `arm64-v8a` APK; the core is bundled as `libstille.so`.

## Debug build and tests

```bash
bash ./tools/build-core.sh     # core → app/src/main/jniLibs/arm64-v8a/libstille.so
./gradlew :app:assembleDebug   # the app
./gradlew :data:testDebugUnitTest :app:testDebugUnitTest
```

`tools/build-core.sh` cross-compiles `core-src/` and installs the binary
into `app/src/main/jniLibs/arm64-v8a/libstille.so` (ignored by Git). APK
packaging fails if the AArch64 core is missing — a Kotlin-only check
(`:app:compileDebugKotlin`) does not need the binary. Native library
extraction stays enabled: Android 10+ forbids executing files from
writable app storage, so an asset copied into `filesDir` cannot be used.

Fast incremental Kotlin check, keeping the Gradle/Kotlin daemons warm:

```bash
./gradlew :app:compileDebugKotlin --daemon --offline --max-workers=2
```

CI (`.github/workflows/ci.yml`) repeats the core build and verifies that
the pinned `CORE-API.md` version matches the codec.

## Release signing

Release builds require a persistent private keystore, provided through
`STILLE_KEYSTORE_PATH`, `STILLE_STORE_PASSWORD`, `STILLE_KEY_ALIAS` and
`STILLE_KEY_PASSWORD`. Debug builds are independent of it. Never commit a
keystore or paste its contents into logs.

### GitHub Actions

The release workflow needs repository secrets
`ANDROID_KEYSTORE_BASE64`, `ANDROID_STORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
`ANDROID_KEY_PASSWORD`, plus the repository variable
`ANDROID_SIGNING_CERT_SHA256` (SHA-256 of the public certificate, 64 hex
characters). Before publication it verifies the APK signature, the pinned
certificate, ZIP integrity and alignment. If the configuration is missing,
the release stops — it never falls back to a generated debug key.

Setting GitHub Actions secrets requires repository Secrets write access;
git push access alone is not enough.

### Local protected layout

For the local layout `~/.local/share/stille-signing/release.p12` and
`release.password` (alias `stille-release`):

```bash
python3 tools/build-signed-release.py --offline
```

The helper never regenerates a missing key, disables Gradle configuration
caching for signing, and runs with one worker, a 1536 MiB Gradle heap and
in-process Kotlin compilation. Keep the directory mode 700, the files 600,
and a secure backup of both outside this repository.

### Why the signing key matters

Published v0.2.3 and v0.2.4 were signed with different ephemeral runner
keys: both APKs verify, but Android cannot install one over the other.
Updating an installed app requires its original private signing key — the
APK contains only the public certificate. The permanent key fixes
continuity for future releases; it cannot repair old installs.

## Troubleshooting

- **"Core unavailable" or invite/avatar actions failing** — the APK lacks
  `lib/arm64-v8a/libstille.so`; rebuild it after
  `bash ./tools/build-core.sh`.
- **Startup failure after an update** — install the new APK *over* the
  existing app. Do not clear app data: identity and history are stored
  locally.
- A redacted diagnostics file (fixed stages and error types only — no
  messages, contacts or secrets) can be shared from Settings by tapping
  **About Stille** five times quickly.
