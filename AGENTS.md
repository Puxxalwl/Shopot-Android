# AGENTS.md — Stille-Messenger client

**English** | [Русский](ru/AGENTS.md)

This file is the repository-wide operating contract for human contributors,
AI agents, reviewers, and maintainers. It adapts the shopot-core
`AGENTS.md` to the Android client layer. The client **never** owns
cryptography, protocol, or persistent format decisions — those live in
shopot-core. The client's job is rendering and transport glue.

## 1. Project invariants

1. **Serverless client.** No project-operated servers, no telemetry, no
   analytics, no crash reporters. The client connects only to the local
   `shopot serve` daemon over UDS.
2. **Minimal dependencies.** Kotlin + Jetpack Compose + Hilt + Coil. No
   Firebase, no third-party SDKs. Each new dependency needs a justification
   in `CHANGELOG.md` and must not touch crypto.
3. **No plaintext on disk.** Avatars and files are stored encrypted by the
   core. Client-side caches are memory-only or encrypted at rest.
4. **Binary protocol only.** The UDS interface is the binary framed protocol
   defined in [`data/src/main/java/com/stille/coreapi/StilleProtocol.kt`](data/src/main/java/com/stille/coreapi/StilleProtocol.kt).
   Never add JSON serialization for the protocol layer; keep the frame format
   from `shopot-core/zig/docs/CORE-API.md` §2 verbatim.
5. **Authentication before trust.** Do not render contact identity, name,
   or avatar until the `CONTACT_JOINED` event confirms the JOIN completed.
6. **Bounded resources.** Message cache ≤ 200 per chat. Avatar cache ≤ 20.
   File cache ≤ 50 MB. No unbounded lists in LazyColumn.
7. **Correctness over speculative speed.** Never skip frame validation,
   sequence checks, or message-id dedup. The core already deduplicates on
   the wire; the client must not show duplicates.
8. **Main thread stays usable.** All UDS traffic runs off-thread. Compose
   recomposition is driven by `StateFlow`/`LiveData` from the foreground
   service; never block on I/O in a `@Composable`.

## 2. Source of truth

- The active implementation of shopot-core lives under
  [`shopot-core/zig/`](https://github.com/Puxxalwl/shopot-core) — the client
  must track the current `CORE-API.md`. Wire changes land in
  `CHANGELOG.md` inside the core repo; check before implementing a new method
  or event.
- Source code (this repo) is authoritative for behavior. The
  `StilleProtocol.kt` binary codec must match `CORE-API.md` byte-for-byte.
- English documentation is normative. Russian mirrors update in the same
  commit — EN + RU must not diverge.

## 3. Toolchain and environment

- **Android Gradle Plugin 8.7.3+**
- **Kotlin 2.1.0** (with compose compiler)
- **Zig 0.17.0** (only for cross-compiling shopot-core; see `tools/`)
- **JDK 17**
- Do not instruct contributors to install OS packages.

## 4. Source layout and ownership

| Subsystem | Files |
|---|---|
| Protocol codec | `data/src/main/java/com/stille/coreapi/` |
| Foreground service | `services/src/main/kotlin/com/stille/messenger/service/` |
| UI components | `app/src/main/java/com/stille/messenger/ui/components/` |
| UI screens | `app/src/main/java/com/stille/messenger/ui/screens/` |
| Theme | `app/src/main/java/com/stille/messenger/ui/theme/` |
| DI | `app/src/main/java/com/stille/messenger/di/` |
| Native binary | `app/src/main/jniLibs/arm64-v8a/shopot` (generated) |

## 5. Kotlin coding rules

### Coroutines & Flow
- All UDS I/O in `Dispatchers.IO` via `UdsClient` service.
- UI collects via `repeatOnLifecycle(Lifecycle.State.STARTED)`.
- Use `StateFlow` for UI state (not `LiveData`).

### Types
- No `Any?` returns from protocol functions; use typed result wrappers.
- Use Kotlin `enum class` for method/event/status IDs — names are the contract.

### Memory
- LazyColumn items are stateless; bind state via `key(msgId)`.
- Avatar/file caches use `LruCache` with explicit size caps.

## 6. No plaintext
- Do not log message bodies, file contents, invite codes, or session keys.
- Diagnostics must redact contact names and identifiers.

## 7. Protocol and persistence rules
- Before using a new CORE-API method, verify its id and payload format against
  `shopot-core/zig/docs/CORE-API.md`.
- Never deviate from the 16-byte LE frame header layout.
- Wire changes require updating the codec AND a CHANGELOG entry here.

## 8. Testing and validation
- `DEBUG_CORE` build flag: connect to a mock UDS server for UI tests.
- Unit tests for the protocol codec (`StilleProtocolTest`).
- Instrumentation tests for main screens (chats list → message list).

## 9. Git, commits, and pull requests
- One concern per commit; small, logical, independently buildable.
- Commit subject: `component: imperative summary`, ≤ 72 chars.
  Prefixes: `ui`, `coreapi`, `service`, `di`, `build`, `docs`, `i18n`.
- Sign off: `Signed-off-by: Name <email>` (DCO).

## 10. Documentation
- Update `README.md` for user-visible setup changes.
- Update this `AGENTS.md` for policy changes.
- Update `DOCS.md` for doc-writing rules (mirrors shopot-core `docs/DOCSTYLE.md`).
- Add a `CHANGELOG.md` entry for every user-visible change.

## 11. AI-agent operating rules
Same as shopot-core: inspect repo instructions and source before editing;
do not invent APIs; keep edits in scope; stop when uncertain.

## 12. License and conduct
Original Android client code: AGPL-3.0-or-later, Copyright (c) 2026
puxxalwl, EryyynIT. See [LICENSE](LICENSE), [NOTICE](NOTICE), and the
upstream license inside the `core-src/` submodule.
