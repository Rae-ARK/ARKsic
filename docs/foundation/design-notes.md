# Design notes

Kind: foundational · Verified: 2026-11-13 · Truth lives in: code structure, `app/src/test/`

The rules the code is held to, and the decisions that are settled. Each entry is a *why*; the *what* is in [`architecture.md`](architecture.md).

## Scope lock

ARKsic plays local **MP3, FLAC, and WAV** files. Nothing else is planned. This
isn't a soft default — it's a constraint other decisions below lean on (e.g.
"one decoding abstraction" only works because the format set is closed and
small). If that changes, this doc changes first, then the proposal that
justifies it.

## Code philosophy

Service-oriented, separated by concern, minimal boilerplate, functional by
default. Four rules:

1. **Services own resources.** Anything holding an external resource or a
   unit of business capability (an open audio line, a decode thread, the
   library's folder tree, the preferences file) is a small service with a
   narrow API, its own file, and constructor-injected dependencies. Add an
   interface only when a second implementation is real or concretely
   planned — `AudioDecoder` qualifies today because MP3/FLAC/WAV genuinely
   need three backends behind one shape; a hypothetical future
   `RemoteLibraryProvider` does not get an interface until it exists.
2. **Layers depend downward.** Views render state and forward intent;
   controllers hold UI-facing state; services hold logic and I/O. No
   sideways reach from one feature's controller into another feature's
   state, no upward reach from a service into a Swing type (a service
   should compile and run with no `javax.swing` import in sight).
3. **Functional by default.** Logic that transforms data is a plain
   (ideally pure) top-level function. A class exists only for state or a
   resource with a lifecycle. Templates: `resolvePlaybackState`,
   `buildShuffleOrder`, `formatTrackDuration`.
4. **Minimal boilerplate.** Smallest construct that says it: a top-level
   `static` method over a one-method class, a `record` over a hand-rolled
   immutable bean, no one-controller-per-window by default, and never one
   giant `MainController` that knows about playback, playlist, and library
   scanning at once.

## UI philosophy

Retro-inspired, playful, but never at the cost of the rules above. The
"fun" lives in the View layer (skins, pixel fonts, meters, animated
transport buttons) — it must never leak into how services are shaped.
A service doesn't know what the UI looks like; a theme swap should touch
zero service code.

## Testing strategy

* **Plain logic:** JVM unit tests in `app/src/test`, no device, no audio
  hardware. Majority tier — this is where `AudioDecoder` selection,
  playlist ordering, shuffle, and format parsing live.
* **UI code:** JVM tests against controllers/view-models with fakes behind
  the service interfaces; Swing components themselves are exercised
  through headless-safe harnesses, not real rendering.
* **Format-backed paths** (actually decoding a real MP3/FLAC/WAV through a
  `javax.sound` line): integration tests gated behind a manual/local run;
  not a CI priority, since they need real audio files and sometimes a real
  output device.
