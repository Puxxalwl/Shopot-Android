# shopot-core — SECURITY.md (reference)

The full threat model, crypto inventory, and security policy live in the
shopot-core repository: [SECURITY.md](https://github.com/Puxxalwl/shopot-core/blob/master/SECURITY.md).

For the Android client:
- The client **never handles keys** — the seed lives at
  `$HOME/.shopot/zig-seed` (0600), owned by the `shopot serve` process.
- No client-side crypto code.
- No telemetry, no crash reporters, no analytics.

Report vulnerabilities privately: open a GitHub Security Advisory on
shopot-core, or email security@shopot.io.
