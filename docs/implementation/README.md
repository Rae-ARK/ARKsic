# Implementation — Index

How the decisions in [foundation](../foundation/README.md) are realized in
code. This section tracks module-level documentation as it's written —
right now it's a map of the modules expected to exist.

## Planned modules

- **`player`** — The playback engine: open a file, decode it (via
  `javax.sound.sampled` for WAV, JLayer/`mp3spi` for MP3), and expose
  play/pause/stop/seek plus position and duration as observable state.
- **`playlist`** — An ordered, mutable track list: add/remove/reorder
  tracks, current-index tracking, and repeat/shuffle modes layered on top
  of "what's next."
- **`metadata`** — Reads tags (title, artist, album, duration) from audio
  files via the chosen tagging library and exposes them as a simple
  read-only model the UI can bind to.
- **`ui`** — Swing views: main window, playlist table, transport controls,
  progress/seek slider. Depends on `player` and `playlist` through
  listener/observer interfaces, never the other way around.
- **`controller`** — Wires UI events (button clicks, menu actions, table
  selection) to calls on `player`/`playlist`. Kept thin on purpose — logic
  belongs in the model classes, not here.

## Conventions for this section

- One doc per module once it has real code behind it (e.g.
  `player.md`, `playlist.md`), linked from the table above.
- Document the *why* behind non-obvious implementation choices (thread
  handling, format quirks) here — the foundation docs cover the big
  architectural picture, this is for the details that live closer to the
  code.
