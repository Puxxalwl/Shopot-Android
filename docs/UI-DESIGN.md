# Stille UI direction
For client contributors and UI implementers. **English** | [Русский](../ru/UI-DESIGN.md)

## Quiet, not decorative

Content leads: conversations are rows with inset separators, not a stack of floating cards. Neutral avatars, generous margins, one blue action color, and consistent typography replace gradients and translucent panels. Grouped surfaces are reserved for settings and forms. The brand logo remains on the welcome screen.

The interface borrows iPhone's hierarchy, not platform-specific assets or expensive optical effects. Android controls keep native semantics, ripple feedback, system animation scaling and at least 48dp touch targets. Text uses sp and forms scroll instead of relying on fixed screen heights. English and Russian labels are supported.

## Interaction and rendering

- Navigation callbacks run immediately; the 180ms crossfade is visual feedback, not a delay before navigation.
- Search filters names locally, with a clear control and explicit no-results state.
- Chat/contact lists use stable contact IDs. Conversation rendering is capped at 200 messages and keyed by message ID.
- Sorting and history preparation are cached. Message delivery flags participate in the history cache key because the existing Message equality excludes flags.
- Avatar loading uses Coil off-thread, with disk and memory caching disabled for this profile request. No bitmap decoding occurs inside composition.
- Sending keeps the keyboard open. The bottom tabs disappear inside a conversation; Android Back returns to the list.
- Profile edits are saved explicitly instead of persisting each keystroke.
- Unimplemented call, attachment and overflow actions are omitted rather than displayed as inert controls.

## Sources consulted with parallel-search

- [Apple HIG — Motion](https://developer.apple.com/design/human-interface-guidelines/motion): purposeful, brief feedback; avoid gratuitous motion in frequent interactions.
- [Apple HIG — Layout](https://developer.apple.com/design/human-interface-guidelines/layout): content/control hierarchy and adaptation to text size.
- [Material 3 — Accessibility structure](https://m3.material.io/foundations/designing/structure): Android touch targets of 48dp and clear navigation.
- [Android — Compose accessibility](https://developer.android.com/develop/ui/compose/accessibility): semantics, scalable content and default component behavior.
- [Android — Compose performance best practices](https://developer.android.com/develop/ui/compose/performance/bestpractices): remembered expensive computations and stable lazy-list keys.
- [Android — Practical performance codelab](https://developer.android.com/codelabs/jetpack-compose-performance): off-thread image loading and measuring rather than assuming improvements.

## Invitations and QR

Invitations use the existing binary `CREATE_INVITE` and `ACCEPT_INVITE` methods. The client keeps codes in memory only, reuses one live code instead of exhausting the core's maximum of four live invites, and waits for `CONTACT_JOINED` before exposing the new contact. The invitation sheet supports copy, Android share, and selecting a QR image. A selected image is decoded in memory; its content is accepted only when it is a bounded `shopot://i/...` or `shopot1.` code.

The QR renderer uses UTF-8 byte mode, error correction H, a four-module quiet zone, black/white modules, and an in-memory symbol-only Stille mark. It verifies the final decorated raster and falls back to a separately verified plain code if the mark reduces decodability. A physical camera, lighting conditions and every display/downscale size still need device validation. The renderer uses a conservative 900-byte input bound after observed detector failures on oversized high-density payloads.

## Validation boundaries

These are implementation decisions, not measured latency claims. No Android device or emulator was attached during this work. Visual review on a physical phone, TalkBack, large font sizes, IME insets and release-mode frame-time measurements remain necessary.

The existing app shell still holds empty contact/message lists and a placeholder send callback; this UI work does not wire the core service into it. Notification selection remains shell-local, as before. Do not mistake a new interface for completed transport integration.
