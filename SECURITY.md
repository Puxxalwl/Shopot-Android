# Security policy

**English** | [Русский](ru/SECURITY.md)

The full threat model and cryptographic inventory live in the
stille-messenger-core repository:
[SECURITY.md](https://github.com/puxxalwl/stille-messenger-core/blob/master/SECURITY.md).

For the Android client:

- The client **never handles keys** — the identity seed lives at
  `~/.stille-messenger/zig-seed` (0600), owned by the
  `stille-messenger-core serve` process.
- No client-side crypto code.
- No telemetry, no crash reporters, no analytics.

## Reporting a vulnerability

Do **not** open a public issue. Use GitHub's private
*Security → Report a vulnerability* tab on this repository or on
[stille-messenger-core](https://github.com/puxxalwl/stille-messenger-core).
Include the affected commit, a description, and a proof of concept if
possible. Reports are acknowledged within 72 hours.
