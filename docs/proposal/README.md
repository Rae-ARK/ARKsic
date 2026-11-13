# Proposal — Index

This section lays out what ARKsic is meant to be before any implementation
detail. Read this first.

## Contents

- **Problem statement** — Most lightweight desktop music players are either
  abandoned Swing relics from the early 2000s or heavyweight frameworks
  (JavaFX, Electron) that are overkill for "play the music files on my
  disk." ARKsic aims to be a small, dependency-light Swing application that
  does local playback well.
- **Goals**
  - Play common local audio formats (MP3 at minimum; WAV via the JDK's
    built-in `javax.sound.sampled` support without extra dependencies).
  - Maintain and reorder a playlist, with repeat/shuffle.
  - Show basic track metadata (title, artist, album, duration) read from
    file tags.
  - Stay a single-window desktop app with a small, auditable dependency
    footprint.
- **Non-goals (for now)**
  - Streaming from remote services.
  - Audio editing, transcoding, or effects processing beyond basic volume.
  - Mobile or web ports — Swing/desktop only.
- **Target platform** — Any OS with a JVM (11+); no OS-specific native
  dependencies planned.

## Status

Proposal stage — the goals above are a starting point and should be revised
as the foundation and implementation take shape. Changes to scope should be
reflected here first, before they show up as code.
