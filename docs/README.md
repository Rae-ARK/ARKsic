# ARKsic Documentation Index

This is the entry point for ARKsic's documentation. Each subfolder has its
own `README.md` that acts as an index for that section — start there rather
than browsing files directly.

| Section | Purpose |
|---|---|
| [`proposal/`](proposal/README.md) | Current scope, and proposals for changing it |
| [`foundation/`](foundation/README.md) | Settled architecture and design decisions |
| [`implementation/`](implementation/README.md) | Features currently being built |

## Reading order

1. **[Proposal](proposal/README.md)** — scope and goals, including anything
   proposed but not yet built.
2. **[Foundation](foundation/README.md)** — *why* the project is structured
   the way it is ([`design-notes.md`](foundation/design-notes.md)) and
   *what* that structure is ([`architecture.md`](foundation/architecture.md)).
3. **[Implementation](implementation/README.md)** — *how* a specific
   in-flight piece works, read before contributing code to it.

## How a change flows through these three

```
proposal/<name>.md  →  implementation/<name>.md  →  foundation/architecture.md
   (argue for it)         (build it)                  (settled, docs updated)
```

## Conventions

- Every `README.md` under `docs/` is an index page, not a dumping ground for
  prose: it should summarize and link out to focused documents rather than
  grow without bound.
- When a new document is added to a section, link it from that section's
  index so nothing goes stale or undiscoverable.
