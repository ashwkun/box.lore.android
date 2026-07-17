# Module README template

Copy this into `<module>/README.md` when creating or backfilling a Gradle module. Keep content **stable**: public APIs and patterns maintainers need, not a dump of every file.

Folder path must equal the Gradle project id (e.g. `:core:playback` → `core/playback/`).

---

# `:group:name`

## Purpose

One short paragraph: what this module owns and what it deliberately does **not** own.

## Public API

Stable types/entry points other modules may depend on:

- `SomeRepository` / `SomeManager` / key Compose screens
- Important contracts (interfaces, factories)
- Things callers must not recreate (singletons, session-scoped graphs)

## Internal structure

High-level packages or folders only:

```text
src/main/java/.../
  foo/     # …
  bar/     # …
```

Call out any service / worker FQCNs that must stay stable across releases.

## Dependencies

Gradle edges (project + notable libs):

- → `:core:model`
- → …

Note forbidden reverse edges (e.g. data ↛ designsystem).

## Testing notes

- Where tests live (`src/test`, `androidTest`)
- Preferred fakes / fixtures (link `:core:testing` when present)
- Smoke or UI tags relevant to this module

## See also

- Root [`ARCHITECTURE.md`](../ARCHITECTURE.md) for cross-module rules
