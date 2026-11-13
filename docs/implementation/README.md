# Implementation — Index

This section tracks **features currently being built**. A doc lives here
only while its feature is in flight — once it lands, the relevant parts of
[`docs/foundation/architecture.md`](../foundation/architecture.md) are
updated and the doc here is retired or folded in. See
[`docs/proposal/README.md`](../proposal/README.md#lifecycle-of-a-proposal)
for how a feature arrives here in the first place.

## In progress

| Doc | Feature | Status |
|---|---|---|
| _(none yet)_ | | |

## Planned modules

For reference, the module boundaries implementation docs are expected to
land within (full rationale in
[`foundation/architecture.md`](../foundation/architecture.md#layers)):

- **`PlaybackService`** — decode (WAV natively, MP3 via JLayer/`mp3spi`,
  FLAC via `jflac-codec`, all through `javax.sound.sampled`) and expose
  play/pause/stop/seek plus position/duration as observable state.
- **`PlaylistService`** — ordered, mutable track list: add/remove/reorder,
  current index, repeat/shuffle.
- **`LibraryService`** — scans configured folder(s) for MP3/FLAC/WAV files
  and maintains the in-memory index.
- **`MetadataService`** — reads title/artist/album/duration tags via a
  tagging library.
- **`PreferencesService`** — persists settings (last folder, volume,
  theme, window size).
- **Controllers + Views** — one controller per cohesive UI surface
  (transport, playlist pane, library browser), Swing views underneath.

## Conventions for this section

- One doc per in-flight feature (e.g. `playback-engine.md`), added to the
  table above when work starts and removed once it lands.
- Document the *why* behind non-obvious implementation choices (thread
  handling, format quirks, retro UI tricks) here while the feature is
  being built — the foundation docs only hold the settled picture.
