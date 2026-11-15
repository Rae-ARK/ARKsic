# Auxio logic port + large-screen Swing UI

Kind: implementation (in flight) · Started: 2026-11-15 · Source: Auxio `dev` @
`f243bce` (GPL-3.0-or-later, same licence as ARKsic)

Re-use Auxio's music-library and playback logic wholesale, translated from
Kotlin to Java, so that the only thing left to design by hand is the UI/UX. The
first UI is a one-to-one Swing take on Auxio's large-screen layout, to be
iterated on later.

> **Process note.** `docs/proposal/README.md` says a scope or architecture
> change is argued as a proposal first. This work was requested directly and
> started without one. Stage 0 below includes writing
> `proposal/auxio-port.md` retroactively so the lifecycle is honoured.

## Status snapshot

| Piece | State |
|---|---|
| Logic port (`arksic.musikr`, `arksic.list`, `arksic.playback`, `arksic.audio`) | Written and committed (`7042730`). Main sources compile with `javac -Xlint:all`. |
| JUnit 5 tests (~60) | Written. **Never run; never compiled against real JUnit.** |
| `build.gradle.kts` | **Not updated** (needs `net.jthink:jaudiotagger`). |
| Wiring into `App`/`Main` | **Not started.** Old `model`/`service`/`view` code still in place and untouched. |
| Swing UI | **Not started.** |
| Real audio playback, seek, ReplayGain | **Unverified** (no audio device in the dev sandbox). |
| `gradle build` | **Unverified** (Gradle and Maven Central unreachable from the sandbox). |

## Interpretation of "the Android 12L part of the Compose"

Auxio has no Compose UI: its screens are XML views + fragments (Compose only
appears in cover/widget code). The Android 12L-style large-screen variants are
the width-qualified resource sets, so the UI target is
**`layout-w720dp/fragment_main.xml`** plus the `sw600dp`/`w600dp`
`fragment_detail.xml` variants. If a different reference was meant, Stage 3
changes; Stages 0–2 do not.

## Source → target map

| Auxio (Kotlin) | ARKsic (Java) |
|---|---|
| `musikr` model, `Name`, `Date`, `ReleaseType`, `Disc` | `arksic.musikr.{model,tag}` |
| `musikr` `MusicGraph`, `TagInterpreter`, `Naming`, `Separators` | `arksic.musikr.{graph,interpret}` |
| `musikr` native TagLib parse + `TagFields` | `arksic.musikr.metadata.TagReader` (jaudiotagger) |
| `musikr` explore/extract/evaluate pipeline | `arksic.musikr.Musikr` |
| Room playlist DB | `arksic.musikr.playlist.StoredPlaylists` (text file) |
| `Sort`, `ListSettings` | `arksic.list` |
| `PlaybackStateManager`, `PlaybackCommand*`, `RawQueue`, `BetterShuffleOrder` | `arksic.playback.state`, `arksic.playback` |
| `ExoPlaybackStateHolder` (ExoPlayer timeline) | `arksic.playback.QueuePlaybackStateHolder` |
| ExoPlayer decode/output | `arksic.audio.SpiAudioEngine` over the existing `AudioDecoder` |
| `ReplayGainAudioProcessor` | `arksic.playback.replaygain.ReplayGain` |
| `PersistenceRepository` (Room) | `arksic.playback.persist.PlaybackPersistence` |

## Deliberate deviations (carry these forward)

- No ICU "Any-Latin" transliteration (Android-only); collation still handles
  non-Latin scripts reasonably.
- No tag cache: every scan re-reads every file. Revisit if scans get slow.
- "Play next", "add to queue" and queue "move" are implemented directly on the
  heap/play order, not via ExoPlayer's `ShuffleOrder` hooks (Auxio's own source
  marks that path as a hack). `BetterShuffleOrder.cloneAndInsert` is ported but
  currently unused.
- Seeking reopens the stream and skips forward (MP3/FLAC SPI streams are not
  random access): O(position) per seek.
- Format model is MP3/FLAC/WAV only, per the scope lock.
- One lock: `PlaybackStateManager`'s monitor. The holder has none of its own and
  engine callbacks re-enter via `runExclusive`, to avoid lock-order inversion
  with the decode thread.

## Stages

Each stage is independently committable and ends with its exit criteria met.
Don't start a stage before the previous one's criteria hold.

### Stage 0 — Make what exists trustworthy

- [ ] Add `net.jthink:jaudiotagger` to `app/build.gradle.kts` (check it against
      `MetadataService`'s existing dependency; keep the small-footprint rule).
- [ ] Run the existing test suite on a machine with Gradle; fix compile and
      assertion failures. Known soft spot:
      `LibraryGraphTest.albumArtistsTakePriorityForAlbumsButNotForSongs` has a
      sloppy assertion that needs rewriting.
- [ ] Add a `TagReader` integration test: generate tagged WAV/FLAC/MP3 fixtures
      in a temp dir, scan with `Musikr.run`, assert the library. Covers
      jaudiotagger behaviour that the synthetic-tag tests can't (multi-value
      artists, ID3 `TXXX` ReplayGain, `TRCK` "3/12", embedded art detection).
- [ ] Write `docs/proposal/auxio-port.md` (see process note) and link it from
      `proposal/README.md`.

**Exit:** `gradle test` green; fixtures round-trip through `Musikr`.

### Stage 1 — Test the playback core against a fake engine

- [ ] `FakeAudioEngine` (records loads, lets a test fire `onEnded`/`onError`).
- [ ] Tests for `QueuePlaybackStateHolder` + `PlaybackStateManager`:
      new playback (shuffled and not, with/without a start song); `next`/`prev`
      incl. wrap-around, rewind-with-prev threshold, `rememberPause`;
      `goTo`; play-next / add-to-queue in both orderings; `move` and `remove`
      in both orderings incl. removing the current and the last song; shuffle
      toggle keeps the current song; repeat NONE/ALL/TRACK at end of track,
      `pauseOnRepeat`; `onError` skips.
- [ ] Saved-state round trip via `PlaybackPersistence` incl. songs deleted from
      the library between save and restore (`applySavedState` re-pointing).
- [ ] Concurrency smoke test: hammer manager calls while firing `onEnded`
      from another thread; assert no deadlock and a consistent queue.

**Exit:** every public queue operation has at least one passing test in each
mode; no known inconsistency between `RawQueue`, the mirror, and the holder.

### Stage 2 — Integrate and retire the old layers

- [ ] An `AppContext` composition root: `MusicRepository`, `StoredPlaylists`
      (path under the user config dir), `PlaybackPersistence`, `ListSettings`,
      `PlaybackSettings`, `SpiAudioEngine`, `QueuePlaybackStateHolder`,
      `PlaybackStateManager`, `PlaybackCommandFactory`.
- [ ] Background scan via `Musikr.run` on start and on "rescan", publishing to
      `MusicRepository`; surface the `failed` file list and progress counter.
- [ ] Debounced save of playback state (the holder's `onStateChanged` hook is
      already there) and restore on launch after the first scan.
- [ ] Persist `PlaybackSettings`/`ListSettings` through `PreferencesService`.
- [ ] Decide fate of old code (see "Decisions needed"): delete or adapt
      `model.Track`, `service.{Playback,Playlist,Library,Metadata}Service`,
      `util.PlaybackMath`, and their tests. Update
      `foundation/architecture.md` layers accordingly.
- [ ] Controllers: new `PlaybackController`/`LibraryController` that adapt the
      manager and repository to the EDT (`SwingUtilities.invokeLater`) and
      expose Swing-free view models, matching the existing controller rule.

**Exit:** app launches, scans a real folder, and plays/queues/seeks audio end
to end through the new stack with a throwaway UI. This is where real-device
checks happen (position drift, gapless-ish transitions, seek latency, ReplayGain
audibly applied, device-unavailable errors).

### Stage 3 — Swing UI: one-to-one first take of the large-screen layout

Target structure from `layout-w720dp/fragment_main.xml` (re-read it before
starting; confirm the home tab set against `fragment_home.xml`):

- **Explore area** (fills the window): the home/detail navigation host.
- **Floating actions** anchored bottom-end over the explore area: a
  speed-dial "new playlist" button and a shuffle-all button.
- **Playback sheet** at the bottom: a collapsed **playback bar**
  (`fragment_playback_bar.xml`) that expands to full height.
- **Expanded sheet** is two columns side by side: **playback panel**
  (`fragment_playback_panel.xml`: cover, title/artist/album, seek bar,
  transport, repeat/shuffle) on the left and the **queue**
  (`fragment_queue.xml`, with a "Queue" header) on the right.
- Scrims over the explore area while the sheet is open.

Swing mapping to decide in this stage: bottom sheet → a `JLayeredPane` or
`OverlayLayout` with an animated height (`javax.swing.Timer`); lists →
`JList`/`JTable` with a cell renderer and cover cache; theming → Material-like
colour tokens in one place (so later restyling is one file). Add a cover loader
with an LRU cache and background decode (`Cover` is either embedded art or a
folder image).

- [ ] Home lists (songs/albums/artists/genres/playlists) using `Sort`.
- [ ] Detail screens (album/artist/genre/playlist) on the `w600dp` two-pane
      variant.
- [ ] Playback bar + panel + queue, with drag-to-reorder (`QueueChange` /
      `UpdateInstructions` give the minimal updates).
- [ ] Headless render check: lay out each panel at 1280×800 and 720×500 into a
      `BufferedImage` for review without a display.

**Exit:** the above renders, is keyboard-operable, and every control calls
through the manager; screenshots attached to the PR.

### Stage 4 — Polish and the "retro" requirement

Auxio's look is Material; ARKsic's design notes ask for retro/playful. After
the one-to-one pass: reskin via the central theme, add the retro touches, then
do accessibility (focus order, accessible names), HiDPI, and window-size
persistence. Each is its own small commit.

### Stage 5 — Landing

- [ ] Fold the settled structure into `foundation/architecture.md`, update
      `design-notes.md` if any rule changed, retire this doc, and mark the
      proposal done.
- [ ] Keep a short `docs/foundation/auxio-attribution.md` (or section) listing
      derived files and the Auxio commit, since logic is a derivative work.

## Decisions needed

1. **Old code**: delete the pre-port `model/service` layers outright, or keep
   them behind adapters during Stage 2? (Recommendation: delete; the new stack
   is a superset and two playlist implementations will rot.)
2. **UI target**: confirm the w720dp layout is the intended "12L" reference.
3. **Seeking cost**: acceptable as-is for a first take, or do we want an
   indexed-seek path for FLAC/WAV (both can seek cheaply) in Stage 2?
4. **Scan cache**: defer, or build a small on-disk tag cache before the UI so
   large libraries feel instant?

## Known risks

- Everything above Stage 0 rests on code that has only been compiled, never
  executed; expect Stage 0/1 to find real bugs.
- jaudiotagger differences between the fork used for compile-checking and the
  `net.jthink` release could surface as compile errors in `TagReader`.
- Real playback needs an audio device; the sandbox had none, so Stage 2's
  device checks must be done on a desktop.
