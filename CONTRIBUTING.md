# Contributing to Stille-Messenger

**English** | [Русский](ru/CONTRIBUTING.md)

Contribution rules, adapted from the core's kernel-style process. Read this
before opening a PR; the contributor contract (including agent rules) is
[AGENTS.md](AGENTS.md).

## 1. Ground rules (invariants — violations are rejected)

- **No servers, no telemetry, no third-party calls in the client.** The
  client connects only to the local `stille-messenger-core serve` daemon
  over UDS.
- **No bundled proprietary code.** No crash reporters, analytics SDKs or
  Firebase. Each new dependency needs a justification in `CHANGELOG.md`
  and must not touch crypto.
- **No plaintext client cache.** Avatars and files come from the core's
  encrypted store; client memory caches are transient and bounded.
- **No JSON on the protocol wire.** The UDS interface is the binary framed
  protocol from `CORE-API.md`. Never serialize protocol data as JSON.
- **No new crypto primitives.** The client never touches keys; all
  cryptography lives in the core.

## 2. Development setup

```bash
git clone https://github.com/Puxxalwl/Stille-Messenger-Android.git
cd Stille-Messenger-Android
bash ./tools/build-core.sh     # requires Zig 0.17.0
./gradlew :app:assembleDebug   # requires JDK 17 + Android SDK
```

Full build, test and release-signing instructions:
[docs/BUILD.md](docs/BUILD.md). Toolchain versions: [AGENTS.md](AGENTS.md) §3.

## 3. Source layout

| Subsystem | Directory |
|---|---|
| Protocol codec | `data/src/main/java/com/stille/coreapi/` |
| Service + UDS | `app/src/main/java/com/stille/messenger/service/` |
| UI screens / components | `app/src/main/java/com/stille/messenger/ui/` |
| Theme | `app/src/main/java/com/stille/messenger/ui/theme/` |
| Recovery storage | `app/src/main/java/com/stille/messenger/backup/` |
| DI | `app/src/main/java/com/stille/messenger/di/` |
| Docs | root `*.md`, `docs/*.md`, `ru/*.md` |

## 4. Commit messages

One logical change per commit. Subject ≤ 72 chars:

```text
ui: fix avatar loading flicker in chat list
```

Prefixes: `ui`, `coreapi`, `service`, `di`, `build`, `docs`, `i18n`.

Body (wrapped 72 cols): what + why, not how. Include how you tested.
Sign off with a `Signed-off-by: Name <email>` line (DCO).

## 5. PR checklist

- [ ] `:app:lint` and `:app:testDebugUnitTest` pass
- [ ] New behavior has tests
- [ ] No plaintext logging of messages, keys or invite codes
- [ ] Wire format matches `CORE-API.md` byte-for-byte
- [ ] Docs updated (EN + RU in the same commit) if user-visible
- [ ] `CHANGELOG.md` entry added

## 6. Review

1. PRs are reviewed against the checklist above.
2. Reviewer comments are addressed in the same branch.
3. Docs are reviewed with the same checklist as code.

## 7. Security

Do not open public issues for vulnerabilities. Use
[SECURITY.md](SECURITY.md).

## 8. License

The original Android client is licensed under AGPL-3.0-or-later. By
contributing, you agree that your contribution is licensed under the same
terms; the `Signed-off-by` line records DCO consent. The `core-src/`
submodule is independent and retains its upstream MIT license.
