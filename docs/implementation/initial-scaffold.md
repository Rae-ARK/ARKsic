# Initial module scaffold

Kind: implementation (in flight) · Started: 2026-11-13

Lays down the Gradle module and the layer skeleton described in
[`foundation/architecture.md`](../foundation/architecture.md) and
[`implementation/README.md`](README.md#planned-modules): one `app`
module, the five services, one controller per UI surface, Swing views
underneath, and a `javax.sound.sampled`-based decoder. No feature work
yet — this is the shape everything else lands into.

## What's here

- `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts` — a
  single-module Gradle build, Java 21 toolchain (see the corrections in
  `architecture.md` below), `application` plugin wired to `arksic.App`.
- `arksic.model` — `Track`, `Preferences` (records), `PlaybackState`,
  `RepeatMode` (enums).
- `arksic.audio` — `AudioFormatType`, the `AudioDecoder` interface, and
  `SpiAudioDecoder`, the one implementation. It imports nothing from
  JLayer/mp3spi/jflac-codec — those are `runtimeOnly` dependencies that
  register `javax.sound.sampled` SPI providers, so `AudioSystem` decodes
  MP3/FLAC the same way it already decodes WAV. This is the trick
  `architecture.md`'s Stack section describes; this scaffold is the first
  code to actually depend on it.
- `arksic.service` — `PlaybackService` (one decode thread, one open
  line), `PlaylistService` (order, current index, repeat/shuffle, with
  the shuffle/advance logic factored out as pure functions),
  `LibraryService` (background folder scan via a single-thread executor),
  `MetadataService` (the one service allowed to import a tagging library
  directly — JAudioTagger), `PreferencesService` (backed by
  `java.util.prefs`, not a hand-rolled file format).
- `arksic.util.PlaybackMath` — the pure, top-level functions
  design-notes.md's code philosophy (rule 3) asks for:
  `resolvePlaybackState`, `buildShuffleOrder`, `nextIndex`,
  `formatTrackDuration`. These are what's actually unit-tested; the
  services mostly delegate to them.
- `arksic.controller` — `TransportController`, `PlaylistController`,
  `LibraryController`. Each renders onto the EDT via
  `SwingUtilities.invokeLater`, never touches a Swing component type
  directly, and never reaches into another controller's service.
- `arksic.view` — `MainFrame`, `TransportView`, `PlaylistView`,
  `LibraryView`, `Icons`. Layout and event wiring only.
- `app/src/test/java` — JUnit 5 tests for `PlaybackMath` and
  `PlaylistService`.

## Corrections folded into `architecture.md`

The Stack table said "Java (11+)". `design-notes.md`'s own code-philosophy
rule ("a `record` over a hand-rolled immutable bean") needs Java 16+; the
`switch` expressions used in `PlaybackMath`, `AudioFormatType`, and
`TransportController` need 14+. Writing the actual code surfaced the
contradiction, so `architecture.md` and `proposal/README.md` first moved
to **Java 17 (LTS)** — the nearest LTS that clears both bars. If this
project wants to target true Java 11 instead, that decision should happen
explicitly (drop records/switch expressions, or revisit rule 4), not as a
side effect of whichever came first.

That pinned the Gradle toolchain (`JavaLanguageVersion.of(17)`) to an
exact major version, which only resolves without a toolchain provisioner
if 17 itself is installed — it won't fall back to a newer JDK already on
the machine. Since local development has JDK 21 installed, the toolchain
was changed to `of(21)` to match what's actually there, and the docs now
say **Java 21 (LTS)** throughout. The same caveat about this being a
documentation decision, not just a build-file tweak, applies again: if
broader compatibility with older JDKs matters more than matching the dev
machine, that should be a deliberate call, not a one-off fix for whoever
happens to run the build next.

## Icon assets — provenance note

`app/src/main/resources/arksic/icons/*.png` (play/pause/stop/next/
previous/shuffle/repeat/volume/list/close) were carried over from
[mustafakbaser/Jukebox-JavaSwing-MySQL](https://github.com/mustafakbaser/Jukebox-JavaSwing-MySQL)
(MIT-licensed) as placeholder transport-control artwork, since nothing
else in that repo fit ARKsic's architecture (it's a NetBeans-form,
JDBC/MySQL, login-gated desktop app — none of which applies here; ARKsic
is local-only with no accounts). Two caveats worth flagging before this
goes further:

1. The MIT license on that repo technically asks for its copyright
   notice to travel with substantial reuse of its content; these icons
   are being carried over without that notice per this project's current
   direction. Worth a second look before a real release.
2. That repo doesn't say whether it drew those icons itself or pulled
   them from an icon pack with its own license — unverified either way.

Treat these as throwaway scaffold placeholders, not final assets.

## Not yet verified by this change

Compiling and testing happened in a sandboxed environment without access
to Maven Central, so:

- `MetadataService` (the only source file that compiles against an
  external library, JAudioTagger) and the JUnit test sources were
  **not** compiled here — Maven Central wasn't reachable to resolve
  `net.jthink:jaudiotagger`, the JUnit BOM, or the MP3/FLAC SPI runtime
  jars.
- Everything else compiles cleanly offline against the JDK alone
  (`javac --release 21`, substituting a trivial stub for
  `MetadataService`'s signature).
- The pure-logic functions in `PlaybackMath` and the playlist
  advance/shuffle/remove/reorder behavior in `PlaylistService` were
  exercised with a plain `main`-based smoke test (JDK only, no JUnit) and
  passed; the real JUnit 5 suite under `app/src/test` covers the same
  ground more thoroughly but needs a real `gradle build` with network
  access to actually run.
- No real MP3/FLAC/WAV file has been played through `PlaybackService`
  end to end — `SpiAudioDecoder` is written to the documented SPI
  contract but unverified against real audio hardware/files.

## Open before this doc retires

- Run a real `gradle build`/`gradle test` with Maven Central reachable to
  confirm the full dependency graph resolves as declared.
- Play an actual MP3, FLAC, and WAV file through `PlaybackService` to
  confirm the SPI providers behave as `architecture.md` describes.
- Replace the salvaged icon set with original artwork (see provenance
  note above) before any real release.
- Once the above hold, fold this doc's settled parts into
  `foundation/architecture.md` and remove it from
  [`implementation/README.md`](README.md)'s in-progress table.
