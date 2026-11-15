<h1 align="center"><b>ARKsic</b></h1>
<h4 align="center">A retro-inspired, local music player for the JVM.</h4>
<p align="center">
    <img alt="License" src="https://img.shields.io/badge/license-GPL%20v3-2B6DBE.svg?style=flat">
    <img alt="Minimum Java" src="https://img.shields.io/badge/Java-21%2B-4B95DE?style=flat">
    <img alt="Build" src="https://img.shields.io/badge/build-Gradle-02303A?style=flat">
</p>
<h4 align="center"><a href="docs/README.md">Docs</a> | <a href="docs/proposal/README.md">Scope &amp; Goals</a> | <a href="docs/foundation/architecture.md">Architecture</a></h4>

## About

ARKsic plays local MP3, FLAC, and WAV files. It's a single-window Swing
desktop app, built with a small, auditable dependency footprint and a
retro-styled, playful UI — not a feature-complete media center. **It
plays music, keeps a playlist, and gets out of the way.**

No screenshots yet — the app itself doesn't exist past this scaffold. This
section gets filled in once there's something to show.

## Features

Scope is locked and tracked in [`docs/proposal/`](docs/proposal/README.md);
changing it starts there, not by quietly adding to this list.

- Play local MP3, FLAC, and WAV files
- Maintain and reorder a playlist, with repeat and shuffle
- Basic track metadata (title, artist, album, duration) read from file tags
- Single-window desktop app, retro-styled and playful by design
- No streaming, no transcoding, no mobile/web port — JVM desktop only

## Building

```bash
git clone https://github.com/Rae-ARK/ARKsic.git
cd ARKsic
./gradlew build
```

Requirements:

- JDK 21 (the Gradle toolchain is pinned to it — see
  [`docs/implementation/initial-scaffold.md`](docs/implementation/initial-scaffold.md)
  for why)
- A network connection the first time, so Gradle can resolve dependencies
  from Maven Central (the MP3/FLAC decoder SPI providers and the tagging
  library)

Run it directly with:

```bash
./gradlew run
```

Run the test suite with:

```bash
./gradlew test
```

## Contributing

Read [`docs/README.md`](docs/README.md) first — in particular, the
[proposal → implementation → foundation](docs/README.md#how-a-change-flows-through-these-three)
flow a change is expected to go through. A scope or architecture change
starts as a proposal doc, not a pull request.

## License

<a href="https://www.gnu.org/licenses/gpl-3.0"><img src="https://www.gnu.org/graphics/gplv3-127x51.png" alt="GNU GPLv3 Image"></a>

ARKsic is  licensed under the
[GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0.en.html)
or later. 
