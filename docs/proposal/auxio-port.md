# Proposal — Port Auxio's library and playback logic

Status: **proposed, retroactive** · Raised: 2026-11-15 · Source project: Auxio
(`OxygenCobalt/Auxio`, `dev` @ `f243bce`, GPL-3.0-or-later)

> Written after the fact. The work was requested directly by the maintainer and
> started before this document existed; this records the reasoning and the scope
> change so the lifecycle in [`README.md`](README.md) is honoured. If the
> proposal is **rejected**, the port can be reverted cleanly: every commit adds
> files and touches no pre-existing code except one small `build.gradle.kts`
> logging block.

## What is being proposed

Translate Auxio's platform-independent logic into Java and make it ARKsic's
library and playback core, so UI/UX is the only part designed from scratch.
Concretely:

- **Library model**: songs, albums, artists, genres and playlists, built by a
  relationship graph that reconciles inconsistent tags (case differences,
  partial MusicBrainz coverage, "various artists" compilations, multi-artist
  tags).
- **Naming and sorting**: numeric-aware, article-stripping sort names; Auxio's
  multi-key sorts per list.
- **Tag interpretation**: separator-based multi-value splitting, ID3 genre
  codes, release types, dates of variable precision, ReplayGain values.
- **Playback state**: a manager/holder pair with a shuffle-aware queue
  (play next, add, move, remove), repeat modes, saved sessions.
- **Large-screen UI target**: Auxio's `layout-w720dp` main screen as the
  one-to-one first Swing take (see the implementation doc).

## How this relates to the settled scope

The settled goals are: play MP3/FLAC/WAV, maintain and reorder a playlist with
repeat/shuffle, show basic metadata, single window, small dependency footprint.
This proposal **keeps the format lock and the Swing/desktop constraint** and
adds beyond the stated goals:

| Beyond current goals | Why it came with the port |
|---|---|
| Browsing by album/artist/genre | Auxio's library model is built around it |
| Folder scanning into a library | Needed to have anything to browse |
| Smart sorting/naming | Core to how Auxio orders every list |
| ReplayGain volume adjustment | Part of Auxio's playback path; a volume adjustment, so within the "no effects beyond basic volume" non-goal, but worth confirming |
| Saved playback session | Auxio restores the queue on launch |

None of these add a format, a streaming source, editing, or transcoding.

## Dependency footprint

**No new dependencies.** The port compiles against `net.jthink:jaudiotagger`
(already declared) and the JDK. Auxio's Android-only pieces (TagLib/JNI,
Room, ExoPlayer, Coil, ICU) are replaced by jaudiotagger, a text file,
`javax.sound`, and plain Java.

## Costs and risks

- **Size**: roughly 6,000 lines of new code plus tests. More surface to
  maintain than the original scaffold.
- **Derivative work**: the logic is a translation of GPL-3.0 code. ARKsic is
  already GPL-3.0, so this is compatible, but attribution must be kept (the
  implementation doc's Stage 5 adds an attribution file; every ported file
  carries a header naming the source).
- **Drift**: Auxio keeps changing. This is a one-time port, not a tracked
  fork; the source commit is recorded so differences can be reviewed later.
- **Audio path is new**: the engine seeks by re-opening and skipping, because
  the MP3/FLAC decoders are not random-access. Acceptable for a first take;
  noted as a decision in the implementation doc.
- **Real playback is unverified by CI** (no audio device on runners).

## Alternatives considered

1. **Keep the scaffold and grow it feature by feature.** Slower, and would
   end up re-deriving the same tag-reconciliation and queue logic with the
   same bugs Auxio already fixed.
2. **Depend on Auxio's code as a library.** Not possible: it is an Android
   application with Android-specific build and APIs.
3. **Port only the playback half.** Possible, but then the library half (the
   hard-won tag logic) is left as hand-rolled parsing, which `design-notes.md`
   argues against.

## Decision needed

Accept, accept with changes, or reject. Questions for the maintainer:

- Is the wider scope (library browsing, scanning, ReplayGain, saved sessions)
  accepted, or should some of it be cut back to the original goals?
- Is Auxio's large-screen layout the intended UI reference?

Implementation lives in
[`../implementation/auxio-port.md`](../implementation/auxio-port.md).
