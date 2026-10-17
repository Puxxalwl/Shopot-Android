# Documentation rules for the Stille-Messenger client

**English** | [Русский](ru/DOCS.md)

Binding rules for writing and changing documentation in this repository.
These mirror the shopot-core [`docs/DOCSTYLE.md`](docs/DOCSTYLE.md) and
apply with equal force. Breaking these rules is review-blocking.

## 1. Layout and languages

- **English is the normative version** at the canonical location (`README.md`,
  `AGENTS.md`, `CONTRIBUTING.md`, `DOCS.md` at the root).
- **Russian mirrors live in `ru/`**: `ru/README.md`, `ru/AGENTS.md`, etc. —
  same filename, no `.ru` suffix (directory carries the language).
- Every doc starts with a language switcher line linking its mirror; both
  directions must work.
- EN and RU must not diverge: a change to one lands in the other **in the
  same commit**. Translation lag is a bug.

## 2. Voice

- Normative and technical. No marketing, no filler, no "simply", "easily",
  "just", no exclamation marks.
- Facts only, present tense, active voice. Describe what the code does today.
- Each doc states its audience in the first two lines (client authors /
  contributors / implementers).

## 3. Facts come from code

- Every byte layout, constant, limit, numeric code, and path is verified
  against `shopot-core/zig/docs/CORE-API.md` and the protocol codec
  `data/src/commonMain/kotlin/com/stille/coreapi/StilleProtocol.kt`.
- Code and docs disagree → one is wrong. Fix both in one commit or file it.
- A doc never specifies behavior that no implementation has.

## 4. Structure

- Byte layouts in fenced code blocks.
- Machine contracts (statuses, methods, events): table with numeric value +
  SCREAMING_SNAKE name + when it occurs.
- One canonical Limits table per doc, pulled from `CORE-API.md` §6.
- Honest limits: state what is not covered.

## 5.