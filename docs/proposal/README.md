# Proposal — Index

This section is where scope and feature changes are argued for *before*
they become architecture or code. Read this first, after the top-level
[docs index](../README.md).

## Current scope (settled)

ARKsic is a local music player for **MP3, FLAC, and WAV** — nothing else is
planned. The UI is retro-inspired and meant to be fun to use, not just
functional. This scope is treated as a constraint elsewhere in the docs
(see [`foundation/design-notes.md`](../foundation/design-notes.md#scope-lock)),
so changing it starts here, not by quietly adding a decoder.

* **Goals**
  - Play MP3, FLAC, and WAV files from local disk.
  - Maintain and reorder a playlist, with repeat/shuffle.
  - Show basic track metadata (title, artist, album, duration) from file
    tags.
  - A single-window desktop app with a small, auditable dependency
    footprint and a retro-styled, playful UI.
* **Non-goals (for now)**
  - Any format beyond MP3/FLAC/WAV (no streaming codecs, no lossy formats
    beyond MP3, no containers like M4A).
  - Streaming from remote services.
  - Audio editing, transcoding, or effects beyond basic volume.
  - Mobile or web ports — Swing/desktop only.
* **Target platform** — Any OS with a JVM (17+); no OS-specific native
  dependencies planned.

## Open proposals

None yet. New feature or scope proposals go here as their own file
(`docs/proposal/<short-name>.md`) and get linked from this table once
added:

| Proposal | Status |
|---|---|
| _(none yet)_ | |

## Lifecycle of a proposal

1. **Written here** as its own doc — problem, proposed change, and impact
   on the current scope/architecture.
2. **Accepted** → a working doc is created in
   [`docs/implementation/`](../implementation/README.md) while it's built.
3. **Landed** → `docs/foundation/architecture.md` (and `design-notes.md` if
   a rule changed) is updated, the implementation doc is retired, and this
   proposal is marked done or removed from the open list.
