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
| Logic port (`arksic.musikr`, `arksic.list`, `arksic.playback`, `arksic.audio`) | Written and committed (`7042730`). Compiles; exercised by 234 tests. |
| JUnit 5 tests | 234 in total. The first 85 ran green in CI. The ~150 added in Stage 0/1 have run **locally only** (through a small JUnit-compatible shim, since Maven Central was unreachable); their first CI run is the next push. |
| `app/build.gradle.kts` | `net.jthink:jaudiotagger:3.0.1` was already a dependency. Stage 0/1 adds per-test result logging so CI output shows what ran. |
| CI | `.github/workflows/ci.yml` runs `gradle build` on push/PR. Reported green on the first run. |
| Proposal | `docs/proposal/auxio-port.md` written (retroactively); awaiting a decision. |
| Wiring into `App`/`Main` | **Not started.** Old `model`/`service`/`view` code still in place and untouched. |
| Swing UI | **Not started.** |
| Real audio output, seek, ReplayGain by ear | **Unverified.** `SpiAudioEngine` has no tests (no audio device in the sandbox or on CI); the playback core is tested against a fake engine only. |
| Gradle wrapper | **Missing** (README references `./gradlew`; it isn't in the repo). |

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

### Stage 0 — Make what exists trustworthy  *(done, pending a CI run of the new tests)*

- [x] jaudiotagger is already declared in `app/build.gradle.kts`; nothing to add.
- [x] First green CI run (`.github/workflows/ci.yml`): reported
      `BUILD SUCCESSFUL`, compile and test tasks executed.
- [ ] Add a Gradle wrapper (`gradle wrapper`, commit `gradlew`, `gradlew.bat`,
      `gradle/wrapper/`). Needs a machine that can run Gradle; CI installs
      Gradle itself in the meantime.
- [x] Fix the sloppy assertion in `LibraryGraphTest`
      (`albumArtistsTakePriorityForAlbumsButNotForSongs` now checks each
      song's own artist and the explicit/implicit album sets).
- [x] `TagReader` integration tests against real files:
      `metadata/AudioFixtures` builds valid WAV (javax.sound), MP3 (a run of
      silent MPEG-1 Layer III frames) and FLAC (STREAMINFO + padding)
      without any encoder; `TagReaderTest` covers core tags in all three
      formats, multi-value artists, sort names, MusicBrainz IDs, ReplayGain
      (including several `TXXX` frames), compilation/composer fallbacks,
      whitespace, Unicode, untagged and corrupt files, and cover detection
      (embedded vs folder image). `MusikrIntegrationTest` scans a real
      directory tree: nesting, non-audio files ignored, unreadable files
      reported, progress count, stable UIDs across rescans, playlists
      surviving a rescan, retagging picked up.
- [x] `docs/proposal/auxio-port.md`, linked from `proposal/README.md`.

**Findings from Stage 0** (all fixed unless noted):

- `TagReader` ignored the separate track/disc *total* fields, so a track of
  `0` with a known total (valid in Auxio's parser) was read as "no track".
  Now combined before parsing.
- `TagReader` silenced jaudiotagger's logging with a logger it did not keep a
  reference to; `LogManager` holds loggers weakly, so the setting could revert
  after a GC. A static reference now pins it.
- `Musikr.run` counted a file and then reported the count in two separate steps,
  so two worker threads could report out of order (a progress bar stepping
  backwards). Counting and reporting now happen under one lock, and the
  callback is documented as serialised and strictly increasing. The
  regression test uses a deliberately slow callback: it fails on the old code
  every time and passes on the new code.
- Test-fixture lessons worth remembering: jaudiotagger returns a bare track
  number and silently drops `"3/12"` set via `FieldKey.TRACK`; several ID3
  `TXXX` frames must be written as one list or each replaces the last (the
  reader already handles the list form); artist MusicBrainz IDs are dropped
  when there is no artist name (the composer fallback does this on purpose).
- Limits of the fixtures: the FLAC fixture has no audio frames (bitrate reads
  as 0 and it cannot be decoded), so nothing here tests decoding. The
  compile/run environment used jaudiotagger built from a GitHub fork;
  differences from `net.jthink:3.0.1` would show up on the next CI run.

**Exit:** met locally; confirm on the next CI run.

### Stage 1 — Test the playback core against a fake engine  *(done, pending a CI run)*

- [x] `FakeAudioEngine` (records loads and seeks, lets a test fire
      `onEnded`/`onError`) and `PlaybackFixture` (a wired stack over a
      synthetic library). `PlaybackAssert.consistent` checks, after every
      operation, that the manager's mirror, the holder's real queue and the
      engine all agree.
- [x] `PlaybackStartTest`, `PlaybackNavigationTest`, `PlaybackQueueEditTest`:
      new playback (shuffled and not, with/without a start song, command
      factory rules); `next`/`prev` incl. wrap-around, rewind threshold,
      `rememberPause`; `goTo`; end-of-track for NONE/ALL/TRACK and
      `pauseOnRepeat`; `onError` skips; seek, pause, progression clamping;
      play-next / add-to-queue; every (current, from, to) move and 300
      random moves; remove before/after/at the current song and at the end,
      removing everything; shuffle toggling. Queue edits are checked in both
      modes against a plain list model.
- [x] `PlaybackPersistenceTest`: file round trip, corrupt/missing files,
      restore into a fresh player (shuffled and not), songs deleted from the
      library between save and restore (including every position of a shuffled
      queue), current song deleted, restore-vs-already-playing rule, playlist
      parents.
- [x] `PlaybackConcurrencyTest`: two UI-style threads issuing arbitrary
      commands, two audio-style threads firing end/error events, and an
      observer asserting the invariants under the manager's lock; plus a
      400-callback test that every audio-thread event is applied exactly once.
- [x] `ReplayGainTest`: every mode, pre-amps, Auxio's quirk that mode OFF
      still applies the "without" pre-amp (pinned as a characterisation),
      sample scaling and clamping, and the holder wiring it into the engine.

**Findings from Stage 1.** The tests found no defects in the holder or
manager. To check the suite is not just agreeing with the code, 13 deliberate
mutations (forgetting an index shift, inserting before instead of after the
current song, not re-pointing a restored shuffle mapping, an off-by-one in the
shuffle move, ...) were applied one at a time: **all 13 were caught**. The
earlier lock-order hazard was also re-introduced on purpose (holder with its
own monitor, engine callbacks bypassing the manager lock): the concurrency
test failed with the engine holding a different song than the queue said.

Not covered, by design: `SpiAudioEngine` itself (position tracking, pause,
seek, end detection) because it opens a real `SourceDataLine`. See Stage 2.

**Exit:** met locally; every public queue operation is tested in each mode.

### Stage 2 — Integrate and retire the old layers

- [ ] Make `SpiAudioEngine` testable: extract the `SourceDataLine` behind a small
      sink interface so position tracking, pause, seek and end detection can be
      tested with a fake sink and a generated WAV, headless. Today it is the
      one untested layer of the playback path.
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

- The playback *core* is now well tested, but the audio *output* layer is
  not (see Stage 2); a real-device pass is still required.
- Tests were compiled and run locally against jaudiotagger built from a
  GitHub fork and a JUnit-compatible shim, not `net.jthink:3.0.1` and real
  JUnit; the next CI run is the real check and may surface small API or
  overload differences.
- Real playback needs an audio device; neither the sandbox nor CI has one, so
  Stage 2's device checks must be done on a desktop.
