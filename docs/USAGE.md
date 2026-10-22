# Using Stille on Android

**English** | [Русский](../ru/USAGE.md)

End-user guide: first launch, contacts, profile and updates. Build and
development instructions live in [BUILD.md](BUILD.md); how the client works
internally — in [ARCHITECTURE.md](ARCHITECTURE.md).

## First launch

Stille opens with a short local setup: interface language, theme (light,
dark or system) and the name shown to your contacts. The name is 1–32
UTF-8 bytes — the same limit the core enforces (16 Cyrillic letters or
8 four-byte emoji). Everything is stored on the device and can be changed
later in Settings and Profile.

No phone number or email is requested at any point.

## Navigation

Profile, Chats, Contacts and Settings are one horizontal space: swipe
between them or use the bottom tabs. Page transitions slide with a short
directional blur on Android 12+; older devices get slide, scale and fade.
Content is sharp at rest.

Inside a conversation and the invitation sheet, swiping between sections is
disabled — the space belongs to that screen. Back from a conversation
returns to the section it was opened from.

## Contacts and invitations

<img align="right" src="assets/stille-owl-invite.svg" width="118" alt="Stille owl holding an invitation">

Open **Add contact** in Chats or Contacts. Stille offers two kinds of
invitation:

- **One-time invite** — a `stille://i/…` code, valid for a single
  acceptance. Share it as a link, show it as a QR code, or let the other
  device scan it with the camera. Up to four one-time codes can be alive
  at once.
- **Permanent link** — `https://stille.im/invite/{code}`, derived from
  your identity seed. It never burns and does not count against the
  one-time limit. The stille.im site only redirects; the link itself
  carries the complete invitation, so it works without any server-side
  state. **Reset link** rotates its secret if the link leaks: shared links
  stop validating immediately, existing contacts are unaffected.

```text
A: create invite → "stille://i/…"        (link, QR, or messenger)
B: accept invite → wait for CONTACT_JOINED → connected
```

The contact appears in the list only after the authenticated
`CONTACT_JOINED` event — never on the provisional name from a pending
acceptance. If two devices accept the same one-time code, the core
serializes them and only the first valid JOIN creates the connection.

Ways to accept: paste a code or link, scan a QR with the live camera
scanner, or pick a saved QR image. Tapping an `https://stille.im/invite/…`
or `stille://…` link in any app opens the same flow — the code is
reconstructed locally, without a network round-trip. Plain `http://`
links are rejected. Legacy `shopot://` and `shopot1.` codes are still
accepted.

Camera frames are decoded in memory by a single bounded worker, never
logged or written to disk; the camera is released when the scanner closes
or the app pauses. Invite codes never appear in logs.

Address-less one-time invites need both devices on the same LAN with
multicast enabled; otherwise use a reachable address or a relay. With the
current core, **Settings → Direct connection (NAT traversal)** enables
attempts to upgrade an existing relay session to a direct peer-to-peer path.
It is enabled by default. Contacts show whether their active route is LAN,
relay, or NAT-punched direct. The relay remains the fallback if either
network/router cannot establish a direct path; NAT traversal is not guaranteed
for every NAT type. Core 2.10 also tries IPv6 direct paths and UPnP mapping
when available, subject to router and network support.

## Profile photo

In Profile, choose **Set photo** / **Change photo**. The system picker
needs no broad storage permission. Images up to 8 MiB are processed
off-thread: EXIF orientation, center square crop, JPEG without metadata,
resized and compressed to the core limit of 16,000 bytes. Success is shown
only after the core confirms the stored content id and size; on failure the
previous photo stays.

Avatars live in the core's encrypted store. The client keeps a memory-only
cache of at most 20 images and shows initials as fallback. Peer photos
require a confirmed JOIN; your own photo reloads through the core API after
a service restart. Camera capture, a manual crop editor and photo removal
are not implemented.

## Account recovery

Recovery moves your *identity* — profile, contacts, routes — to a new
device; message history is never synced and never leaves the device. The
backup is a single core-sealed file stored in your own cloud (Google
Drive, Yandex Disk or any WebDAV server), unlocked by a passkey or a
recovery code.

The full ceremony, file layout and provider specifics:
[RECOVERY.md](RECOVERY.md).

## Updates

Stille can check the public GitHub Releases feed once per launch; the
check can be disabled in Settings, and a manual check remains available
under **About Stille**. An update downloads only after confirmation and is
verified — checksum, signing certificate and version — before Android's
system installer opens. The "install unknown apps" permission is requested
*before* the download starts, not after it.

Do not uninstall the app or clear its data to troubleshoot an update:
identity and history are stored locally and may be lost. Updating means
installing the new APK over the existing installation.
