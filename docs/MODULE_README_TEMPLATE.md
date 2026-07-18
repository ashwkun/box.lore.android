# Module README template

Copy this into `<module>/README.md` when creating or backfilling a Gradle module.

**Rules**

- Folder path must equal the Gradle project id (e.g. `:core:playback` → `core/playback/`).
- Keep content **stable**: public APIs and patterns maintainers need — not a dump of every file.
- A phase that creates or moves code into a module is **not done** until this README is comprehensively updated.
- Root `README.md` / CHANGELOG are **not** substitutes for module READMEs.
- Cross-cutting maps live in [`ARCHITECTURE.md`](../ARCHITECTURE.md) and [`docs/TESTING.md`](TESTING.md).

See program plan: [`docs/PLAN_MODULAR_ANDROID_HARDENING.md`](PLAN_MODULAR_ANDROID_HARDENING.md).

---

# `:group:name`

## Purpose

One short paragraph: what this module owns and what it deliberately does **not** own.

## Public API

Stable types/entry points other modules may depend on:

- Repositories / managers / ports / key Compose screens
- Important contracts (interfaces, factories, assemblers)
- Things callers must **not** recreate (Application-scoped instances)

## Internal structure

High-level packages or folders only:

```text
src/main/java/.../
  foo/     # …
  bar/     # …
```

## Dependencies

Gradle edges (project + notable libs):

- → `:core:model`
- → …

Forbidden reverse edges (e.g. catalog/data ↛ designsystem; no feature → feature).

## Threading / lifecycle

- What runs on Main vs IO / default dispatchers
- Application-scoped vs Activity / ViewModel-scoped objects
- Any Service / WorkManager lifecycle notes

## Persistence & identity

Call out anything that must stay stable across releases:

- SharedPreferences / DataStore names and keys
- Room DB filenames
- Worker / Service FQCNs (table if multiple)
- ID schemes (`rss:`, mediaId prefixes, etc.)

## Testing notes

- Where tests live (`src/test`, `androidTest`)
- Preferred fakes / fixtures (link `:core:testing`)
- Compose `testTag`s (if UI module)
- How to run locally:

```bash
./gradlew :<module>:testDebugUnitTest
```

## CI relevance

Which GitHub Actions workflows exercise this module (unit, instrumented, coverage), or “local only”.

## See also

- Root [`ARCHITECTURE.md`](../ARCHITECTURE.md)
- [`docs/TESTING.md`](TESTING.md)
- [`docs/PLAN_MODULAR_ANDROID_HARDENING.md`](PLAN_MODULAR_ANDROID_HARDENING.md) (while program is active)
- Related module READMEs
