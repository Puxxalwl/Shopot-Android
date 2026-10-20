# Stille UI direction

**English** | [Русский](../ru/UI-DESIGN.md)

For client contributors and UI implementers. This describes the visual and
interaction rules the interface follows — not a component inventory.

## Quiet, not decorative

Content leads: conversations are rows with inset separators, not a stack
of floating cards. Neutral avatars, generous margins, one blue action
color and consistent typography. Grouped glass surfaces — a 40% translucent
fill with a soft gradient rim, no expensive blur or shadow layers — are
reserved for settings and forms. The dark appearance uses the Midnight
navy palette. The brand logo appears on the welcome screen; the owl mascot
marks the empty chats state.

The interface borrows iOS hierarchy, not platform-specific assets or
optical effects. Android controls keep native semantics, ripple feedback,
system animation scaling and at least 48dp touch targets. Text uses sp and
forms scroll instead of relying on fixed screen heights. English and
Russian labels are supported.

## Interaction and rendering

- Main sections share a HorizontalPager: swipe left/right or tap a bottom
  tab. Chats is the initial page; tab selection follows the pager. Taps
  animate with a 300 ms eased slide; rapid taps cancel the previous
  navigation animation. Page keys preserve saved scroll and form state.
  Conversations open above the pager; Back returns to the originating
  section.
- During movement, directional GPU blur peaks at 5 dp horizontally / 0.6 dp
  vertically and disappears completely at rest. Effects are preallocated in
  12 steps and applied in graphics layers, not by capturing screenshots.
  API 31+ supports blur; older versions retain slide, subtle scale and
  fade. Blur follows the Compose motion-duration scale and is disabled
  when it is zero. This approximates motion blur rather than accumulating
  frames.
- Search filters names locally, with a clear control and an explicit
  no-results state.
- Chat and contact lists use stable contact IDs. Conversation rendering is
  capped at 200 messages and keyed by message ID.
- Sorting and history preparation are cached. Message delivery flags
  participate in the history cache key because message equality excludes
  flags.
- Avatar loading uses Coil off-thread, with disk and memory caching
  disabled for the profile request. No bitmap decoding happens inside
  composition.
- Sending keeps the keyboard open. Bottom tabs disappear inside a
  conversation; Android Back returns to the list.
- Profile edits are saved explicitly, not on each keystroke.
- Unimplemented call and attachment actions are omitted rather than shown
  as inert controls.

## Invitations and QR

Invitations use the binary `CREATE_INVITE` / `CREATE_INVITE_EX` and
`ACCEPT_INVITE` methods. The client keeps invite codes in memory only,
reuses one live code instead of exhausting the core's maximum of four, and
waits for `CONTACT_JOINED` before exposing a new contact. Besides the
one-time code, the invitation sheet shows the permanent
`stille.im/invite/{code}` link (profile code, never burns); both are
accepted via copy, share, QR image, live camera scan or deeplink through
one parser. A selected image is decoded in memory; its content is accepted
only when it is a bounded `stille://i/…` (legacy `shopot://i/…`) or
`shopot1.` code.

The QR renderer uses UTF-8 byte mode, error correction H, a four-module
quiet zone, black/white modules, and an in-memory symbol-only Stille mark.
It verifies the final decorated raster and falls back to a separately
verified plain code if the mark reduces decodability. The renderer uses a
conservative 900-byte input bound after observed detector failures on
oversized high-density payloads. Physical camera, lighting and every
display/downscale size still need device validation.

## References

- [Apple HIG — Motion](https://developer.apple.com/design/human-interface-guidelines/motion):
  purposeful, brief feedback; avoid gratuitous motion in frequent
  interactions.
- [Apple HIG — Layout](https://developer.apple.com/design/human-interface-guidelines/layout):
  content/control hierarchy and adaptation to text size.
- [Material 3 — Accessibility structure](https://m3.material.io/foundations/designing/structure):
  48dp touch targets and clear navigation.
- [Compose accessibility](https://developer.android.com/develop/ui/compose/accessibility):
  semantics, scalable content, default component behavior.
- [Compose performance best practices](https://developer.android.com/develop/ui/compose/performance/bestpractices):
  remembered expensive computations, stable lazy-list keys.

## Validation boundaries

These are implementation decisions, not measured latency claims. Frame
timing on physical devices, TalkBack review, large font sizes and IME
insets remain open validation work; treat any visual claim here as
reviewable intent, not a benchmark.
