# Foundation — Index

The settled architectural and technical decisions ARKsic is built on. Read
this after the [proposal](../proposal/README.md) and before the
[implementation](../implementation/README.md) docs. This index only
summarizes and links — if you're adding detail, it belongs in one of the
documents below, not here.

## Contents

| Doc | Covers |
|---|---|
| [`design-notes.md`](design-notes.md) | Code philosophy, scope lock, testing strategy — the *why* |
| [`architecture.md`](architecture.md) | Stack, layers, ownership, local state, and lifecycle — the *what* |

## How this section changes

Foundation docs describe decisions that are **settled**, not ones in
flight:

* A new feature or scope change starts as an entry in
  [`docs/proposal/`](../proposal/README.md).
* A feature actively being built gets a working doc in
  [`docs/implementation/`](../implementation/README.md).
* Once it lands, `architecture.md` and/or `design-notes.md` are updated to
  reflect the new settled state, and the implementation doc is retired or
  folded in.

If code and a foundation doc disagree, the code structure and tests are the
source of truth — the doc is wrong and should be fixed, not the other way
around.
