# Contributing to Shopot Android

**English** | [Русский](ru/CONTRIBUTING.md)

Contribution rules, adapted from shopot-core's kernel-style process.
Read this before opening a PR.

## 1. Ground rules (invariants — violations are rejected)

- **No servers, no telemetry, no third-party calls in the client.** The
  client connects only to the local `shopot serve` daemon over UDS.
- **std-only core.** Do not bundle proprietary code, crash reporters,
  analytics SDKs, or Firebase.
- **No plaintext client cache.** Avatars and files come from the core's
  encrypted store; client memory caches are transient only.
- **No JSON on the protocol wire.** The UDS interface is the binary framed
  protocol from `CORE-API.md`. Never serialize protocol data as JSON.
- **No new crypto primitives.** The client never touches keys.

## 2. Development setup

```bash
git clone https://github.com/Shopot/Shopot-Android.git
cd Shopot-Android
# Android Studio: open project, sync Gradle
# Or CLI (requires JDK 17 + Android SDK):
./gradlew :app:assembleDebug
```

**Toolchain**: JDK 17, Android Gradle Plugin, Kotlin 2.1.0. See
[`AGENTS.md`](AGENTS.md) §3 for exact versions.

## 3. Source layout

| Subsystem | Directory |
|---|---|
| Protocol codec | `data/src/commonMain/kotlin/com/shopot/coreapi/` |
| Service + UDS | `app/src/main/java/com/shopot/messenger/service/` |
| UI components | `app/src/main/java/com/shopot/messenger/ui/components/` |
| UI screens | `app/src/main/java/com/shopot/messenger/ui/screens/` |
| Theme | `app/src/main/java/com/shopot/messenger/ui/theme/` |
| DI | `app/src/main/java/com/shopot/messenger/di/` |
| Resources | `app/src/main/res/` |
| Docs | root `*.md`, `ru/*.md` |

## 4. Commit messages

One logical change per commit. Subject ≤ 72 chars:

```text
ui: fix avatar loading flicker in chat list
```

Prefixes: `ui`, `coreapi`, `service`, `di`, `build`, `docs`, `i18n`.

Body (wrapped 72 cols): what + why, not how. Include how you tested.

Sign off:

```text
Signed-off-by: Name <email>
```

## 5. PR checklist

- [ ] `:app:lint` and `:app:testDebugUnitTest` pass
- [ ] `zig fmt --check .` on any protocol codec changes
- [ ] New behavior has tests
- [ ] No plaintext logging of messages, keys, or invite codes
- [ ] Wire format matches `CORE-API.md`
- [ ] Docs updated (EN + RU) if user-visible
- [ ] CHANGELOG.md entry added

## 6. Review

1. PRs reviewed against the checklist above.
2. Reviewer comments addressed in the same branch.
3. Docs are reviewed with the same checklist as code.

## 7. Security

Do not open public issues for vulnerabilities. Use
[SECURITY.md](SECURITY.md).

## 8. License

The original Android client is licensed under AGPL-3.0-or-later. By
contributing, you agree that your contribution is licensed under the same
terms. The `Signed-off-by` line records DCO consent. The `core-src/` submodule
is independent and retains shopot-core's upstream license.
