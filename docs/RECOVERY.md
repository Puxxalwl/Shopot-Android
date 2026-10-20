# Account recovery

**English** | [Русский](../ru/RECOVERY.md)

Stille supports a **minimal recovery package** so a new device can continue
using the same cryptographic identity. It is deliberately *not* an account
sync: no message history, no drafts, no files ever leave the device.

The recovery blob is **owned by the core** (`CREATE_RECOVERY_BACKUP` /
`RESTORE_RECOVERY_BACKUP`, API 2.6.0 — `CORE-API.md` §4.10). The client
never parses, re-encrypts or derives keys for the blob: it only runs the
unlock ceremony and stores the sealed package in the user's own cloud.

## What is backed up

The chosen cloud storage holds exactly two opaque files — in Google Drive's
hidden `appDataFolder` scope, or in a dedicated directory on a WebDAV
server:

```text
appDataFolder/stille-recovery-v1.bin   — core-sealed recovery blob   (Drive)
appDataFolder/stille-recovery-v1.meta  — public sidecar              (Drive)
/stille-backup/stille-recovery-v1.bin  — core-sealed recovery blob   (WebDAV)
/stille-backup/stille-recovery-v1.meta — public sidecar              (WebDAV)
```

The blob is produced by the core: ChaCha20-Poly1305 with a key derived
(HKDF-SHA256) from the 32-byte unlock secret. It contains the identity
seed, profile, contacts with routes, and the block list — everything needed
to keep talking to the same people. It contains **no message history** and
is capped at 92,786 bytes (`Limits.RECOVERY_BLOB`).

The sidecar is public metadata, not secret (CLIENT_RULES.md §4.2):

```text
magic      "STME"
version    1
key_source 1 = passkey PRF | 2 = recovery code
prf_salt   32 random bytes (passkey source only)
created_ms upload date
api_version e.g. "2.6.0"
```

## Unlock secret

Two sources (CLIENT_RULES.md §4.1), both 32 bytes, both never stored:

1. **Passkey PRF** (preferred). A WebAuthn passkey synced with the Google
   account is created once at enrollment together with the random PRF
   salt. Evaluating the same (passkey, salt) pair on any device yields the
   same secret. RP id `stille.app`.
2. **Recovery code** (mandatory fallback). If passkey enrollment or PRF is
   unavailable (including a missing domain association), 32 random bytes are
   shown to the user exactly once (64 hex chars in groups of 8) and never
   stored. Cancelling confirmation does not start the fallback.

Google Drive authorization uses the platform's `oauth2:` token type and an
Activity-backed consent prompt; when Google requires interactive consent,
the client opens the consent screen and automatically retries the failed
operation after access is granted. Network and token requests run off the
main thread; credential prompts and UI results run on the main thread.
Google OAuth configuration and domain association are still release
prerequisites.

## Storage providers

The copy always lives in the user's own cloud; Stille operates no servers.

| Provider | Transport | Login |
|---|---|---|
| Google Drive | Drive REST v3, `drive.appdata` scope | Android account picker |
| Yandex Disk | WebDAV over HTTPS (`webdav.yandex.ru`) | username + app password |
| Other WebDAV (Nextcloud, ownCloud, …) | WebDAV over HTTPS | username + password |

WebDAV credentials are entered once and reused for refresh/delete. The
password is stored only in encrypted form (AES-256-GCM, key held by the
Android Keystore) inside the app's private storage, and the server address
must be HTTPS. Yandex requires a dedicated **app password** for external
apps (id.yandex.ru → Security); the account password will not work.
WebDAV uploads `PUT` the files in place, so a failed upload never destroys
the last good copy.

## User flow

Enable on the old device (Settings → Account recovery):

```text
Enable recovery
→ create passkey (or receive the recovery code)
→ CREATE_RECOVERY_BACKUP(unlock_secret)
→ upload blob + sidecar to the chosen cloud
```

Backup cadence: re-run the flow ("Update backup") after profile-affecting
changes (name, avatar, contacts, routes, blocks). Uploads replace the
files in place, so a failed upload never destroys the last good copy.

Restore on a new device (Welcome screen → Restore account):

```text
start the core in recovery mode (serve without a seed)
→ choose storage (Google Drive / Yandex Disk / WebDAV)
→ confirm passkey / enter the code
→ RESTORE_RECOVERY_BACKUP(blob, unlock_secret)
→ same account, empty history
```

The daemon starts **unprovisioned** (`StilleService.startRecovery`): no
local seed is created, `GET_IDENTITY` answers `NOT_INITIALIZED`, and the
UDS API waits for method 42. Restore applies only to the empty profile
(`ALREADY_PROVISIONED` otherwise — a live profile is never overwritten).
A wrong secret or a corrupt blob yields `BAD_RECOVERY` without side
effects. On success the identity is swapped in place, the profile
(name, avatar, contacts, routes) reloads, and networking comes up by
itself. Cancelling the dialog stops the unprovisioned daemon.

## Security properties

- Google only ever stores the sealed blob; the key never leaves the
  device's authenticator or the user's hands.
- The client never logs, parses or persists the unlock secret or the blob.
- The core re-reads the seed from disk when sealing; if the seed file is
  gone the package cannot be created (`NOT_INITIALIZED`).
- The seed file itself is never transmitted; it is restored by the core
  through its own sealed path.

## Release prerequisites

- `drive.appdata` OAuth scope requires Google verification for
  production builds.
- WebDAV providers need no registration or project-side setup.
- RP id `stille.app` must be linked via Digital Asset Links before the
  passkey ceremony works in release builds.
- PRF support depends on the platform authenticator; the recovery-code
  fallback must stay reachable.
