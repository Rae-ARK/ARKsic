# Architecture

Kind: foundational · Verified: 2026-11-13 · Truth lives in: code structure, `app/src/test/`

> **Correction (2026-11-13):** the Stack table below previously said "Java
> (11+)". That's incompatible with design-notes.md's own rule 4 ("a
> `record` over a hand-rolled immutable bean") — records need Java 16+,
> and the controller/model layer uses `switch` expressions too (14+). The
> row said Java 17 (LTS), the lowest LTS release that supports both, until
> the pinned Gradle toolchain was changed to match the JDK actually
> available in development (21) — so it now says Java 21 (LTS). Found and
> fixed while scaffolding the initial module skeleton; see
> [`docs/implementation/initial-scaffold.md`](../implementation/initial-scaffold.md).

The *what*. The *why* behind these choices is in [`design-notes.md`](design-notes.md).
Feature additions that would change this doc start as a proposal
(`docs/proposal/`); a feature actively being built against this
architecture gets a working doc in `docs/implementation/` until it lands,
at which point this doc is updated and the implementation doc is retired.

## Stack

| Layer | Choice |
|---|---|
| Language / runtime | Java 21 (LTS), no OS-native dependencies |
| UI toolkit | Swing |
| WAV decoding | `javax.sound.sampled` (built in — no dependency) |
| MP3 decoding | JLayer, fronted by an `mp3spi` `javax.sound` SPI provider |
| FLAC decoding | `jflac-codec`, a `javax.sound` SPI provider |
| Tagging / metadata | A dedicated tag-reading library (e.g. JAudioTagger), not hand-rolled parsing |
| Build | Gradle |

The MP3 and FLAC choices both land on the same trick: expose the format as
a `javax.sound.sampled` SPI provider so the rest of the app opens an
`AudioInputStream` the same way regardless of format. WAV needs no SPI at
all. This is why a single `AudioDecoder` abstraction can cover all three
planned formats without per-format branching above the decoding layer —
see [design-notes.md](design-notes.md) for why that's load-bearing on the
"MP3/FLAC/WAV and nothing else" scope lock.

## Layers

```
View (Swing)
   ↓ renders state, forwards intent
Controller
   ↓ translates intent into service calls, holds UI-facing state
Service
   ↓ owns a resource or a capability; does the actual work
javax.sound / filesystem / Preferences
```

* **View.** Swing components only. No logic beyond laying out what it's
  given and calling a controller method on user input. Never talks to a
  service directly.
* **Controller.** One per cohesive UI surface (transport controls,
  playlist pane, library browser) — not one per window, and never a
  single `MainController`. Holds state the view needs to render (current
  track label, playing/paused, selected row) and forwards user intent to
  services. Stateless where it can be; a plain function where a class
  would just be a method.
* **Service.** Owns a resource or capability end to end:
  * `PlaybackService` — owns the open `javax.sound` line and the decode
    thread for whatever is currently playing.
  * `PlaylistService` — owns the in-memory ordered track list, current
    index, and shuffle/repeat state.
  * `LibraryService` — owns the on-disk scan of the music folder(s) and
    the in-memory index built from it.
  * `MetadataService` — owns tag reads (title/artist/album/duration) via
    the tagging library.
  * `PreferencesService` — owns the on-disk settings file (last folder,
    volume, theme, window size).

  Services never import a Swing type and never reach sideways into another
  service's private state — if `PlaybackService` needs to know what's
  queued, it's told the next track; it doesn't reach into
  `PlaylistService`'s internals.

## Ownership

Resource ownership is single and explicit — exactly one service owns each
externally-held thing, and everything else only sees what that service
chooses to expose:

| Resource | Owner | Released when |
|---|---|---|
| Open `SourceDataLine`/`Clip` + decode thread | `PlaybackService` | Track changes, playback stops, or app shuts down |
| In-memory playlist order | `PlaylistService` | Playlist is cleared or app shuts down |
| Library scan cache | `LibraryService` | Folder is rescanned or app shuts down |
| Settings file handle | `PreferencesService` | Flushed on every change; closed at shutdown |
| Swing components | Whichever View created them | Window/dialog disposal |

A controller or view never holds an audio line, a file handle, or a raw
decode thread directly — if it needs one, that's a sign the capability
belongs in a service instead.

## Local vs. shared state

Not everything needs to live in a service. The rule: **state a single view
needs to render itself, and that nothing else depends on, stays local to
that view/controller; state more than one surface needs, or that must
survive the view being torn down, lives in a service.**

Examples:

* The pixel position of a slider mid-drag — local to the transport
  controller until the drag ends.
* "Is the playlist pane currently showing search results or the full
  list" — local UI state, not something `PlaylistService` needs to know.
* The actual playback position (for the progress bar *and* for resuming
  after a seek) — owned by `PlaybackService`, because both the transport
  view and a potential "resume where I left off" feature need it.
* Current volume — owned by `PreferencesService` (it's persisted) and
  mirrored into the transport controller for rendering; the controller
  never treats its mirrored copy as the source of truth.

## Lifecycle

**Startup**
1. `PreferencesService` loads settings from disk (or defaults if absent).
2. Other services are constructed with their dependencies injected —
   no service reaches out and finds its own dependencies.
3. The main window is built and shown on the Event Dispatch Thread (EDT).
4. If a library folder is already known, `LibraryService` kicks off a scan
   off the EDT; the view renders an empty/loading state until it reports
   back.

**Track playback**
1. Controller asks `PlaylistService` for the track to play.
2. Controller asks `PlaybackService` to play it; `PlaybackService` picks
   the right decoder path (WAV directly, MP3/FLAC via their SPI
   providers), opens the line, and starts its own decode/playback thread.
3. `PlaybackService` pushes position/duration/state updates; the
   controller marshals them onto the EDT (`SwingUtilities.invokeLater`)
   before touching any Swing component.
4. On track end, stop, or error, `PlaybackService` releases the line and
   thread before reporting the new state — never leaves a dangling line
   open "just in case."

**Shutdown**
1. `PlaybackService` stops playback and releases the audio line.
2. `PreferencesService` flushes any pending changes.
3. `LibraryService` abandons any in-flight scan cleanly (no orphaned
   background thread outliving the window).

## Concurrency model

* The **EDT** renders views and handles input — nothing that can block
  (decoding, disk scans, file I/O) runs there.
* Each playing track gets its **own decode/playback thread**, owned and
  joined by `PlaybackService`; only one such thread is alive per
  `PlaybackService` instance at a time.
* **Library scans** run on a background thread owned by `LibraryService`,
  reporting incremental results back to the EDT rather than blocking the
  UI until the whole folder tree is walked.
* Any state a service pushes toward the UI crosses back onto the EDT via
  `SwingUtilities.invokeLater` (or an equivalent listener dispatch) — a
  service never calls into a Swing component from a background thread.
