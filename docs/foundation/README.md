# Foundation — Index

The architectural and technical decisions ARKsic is built on. Read this
after the [proposal](../proposal/README.md) and before the
[implementation](../implementation/README.md) docs.

## Contents

- **UI toolkit — Swing.** Chosen over JavaFX for minimal setup (ships with
  the JDK, no separate module to add) and because the UI needs here —
  a window, a playlist table, transport buttons, a progress slider — are
  squarely within what Swing does well.
- **Audio decoding.** `javax.sound.sampled` handles WAV natively. MP3 is not
  supported out of the box by the JDK, so decoding goes through the
  JavaZOOM/JLayer decoder (optionally fronted by an SPI provider such as
  `mp3spi` so MP3 streams can be opened through the same `javax.sound`
  `Clip`/`SourceDataLine` APIs as WAV). This keeps the playback code mostly
  format-agnostic above the decoding layer.
- **Metadata/tagging.** Reading ID3 tags (title/artist/album/duration) is
  planned via a dedicated tagging library (e.g. JAudioTagger or mp3agic)
  rather than hand-rolled ID3 parsing.
- **Architecture pattern — MVC**, with the player/playlist state as the
  Model, Swing components as the View, and a thin Controller layer wiring
  button/menu actions to model changes. The model also acts as the Subject
  in an Observer relationship so the UI (progress bar, now-playing label)
  updates reactively instead of being polled.
- **Concurrency.** Playback runs off the Event Dispatch Thread (its own
  player thread), with UI updates marshalled back onto the EDT via
  `SwingUtilities.invokeLater` — this avoids audio decoding blocking the UI
  and avoids touching Swing components from a background thread.

## Open questions

Decisions above that are still tentative should be tracked here with the
tradeoffs considered, so later contributors understand *why*, not just
*what*.
