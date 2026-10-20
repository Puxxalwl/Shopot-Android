# Documentation rules for the Stille-Messenger client

**English** | [Русский](ru/DOCS.md)

Binding rules for writing and changing documentation in this repository.
They mirror the stille-messenger-core
[`docs/DOCSTYLE.md`](core-src/docs/DOCSTYLE.md) and apply with equal force.
Breaking these rules is review-blocking.

## 1. Layout and languages

- **English is the normative version** at the canonical location: root
  `README.md`, `AGENTS.md`, `CONTRIBUTING.md`, `DOCS.md`, `SECURITY.md`,
  `CODE_OF_CONDUCT.md`, and `docs/*.md`.
- **Russian mirrors live in `ru/`**: `ru/README.md`, `ru/USAGE.md`, … —
  same filename in `ru/`, no `.ru` suffix; the directory carries the
  language.
- Every doc starts with a language switcher line linking its mirror; both
  directions must work.
- EN and RU must not diverge: a change to one lands in the other **in the
  same commit**. Translation lag is a bug.

## 2. Document map

| Document | Owns |
|---|---|
| `README.md` | Identity, feature summary, docs index, build quickstart |
| `docs/USAGE.md` | End-user behavior of the app |
| `docs/BUILD.md` | Building, testing, signing, publishing |
| `docs/ARCHITECTURE.md` | Client internals, core boundary, invariants |
| `docs/RECOVERY.md` | Recovery ceremony, blob, storage providers |
| `docs/UI-DESIGN.md` | Visual and interaction direction |

One topic, one home. Other documents link to it instead of restating it.

## 3. Voice

- Normative and technical. No marketing, no filler, no "simply", "easily",
  "just", no exclamation marks.
- Facts only, present tense, active voice. Describe what the code does
  today.
- Each doc states its audience in the first two lines (end users /
  contributors / implementers).

## 4. Facts come from code

- Every byte layout, constant, limit, numeric code and path is verified
  against `core-src/core/docs/CORE-API.md` and the protocol codec
  `data/src/main/java/com/stille/coreapi/`.
- Code and docs disagree → one is wrong. Fix both in one commit or file it.
- A doc never specifies behavior that no implementation has.

## 5. Structure

- Byte layouts in fenced code blocks.
- Machine contracts (statuses, methods, events): table with numeric value +
  SCREAMING_SNAKE name + when it occurs.
- One canonical Limits table per doc, pulled from `CORE-API.md` §6.
- Honest limits: state what is not covered.

## 6. Notation

- `u8`/`u16`/`u32`/`u64` — unsigned integers.
- `str16`/`str32` = `[len u16|u32][bytes]` (UTF-8, no null terminator).
- Hex with `0x` prefix for constants.

## 7. Change discipline

- A protocol change requires updating the codec and `CHANGELOG.md` in the
  same commit.
- User-visible changes update the relevant `docs/` page and its RU mirror.
