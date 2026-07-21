# Contributing to Tonnet Mobile

## Development baseline

- Node.js 22 or newer and npm 10 or newer
- Java 21, Android SDK 36, Android NDK 27.1, and the official Go 1.25.5 toolchain for Android work
- ShellCheck available on `PATH` for the repository quality gate

Install the exact dependency graph with `npm ci`. Before opening a pull request, run:

```bash
npm run check
npm run build
```

For Android changes, also run:

```bash
npm run cap:sync
cd android
./gradlew spotlessCheck testProdDebugUnitTest lintProdDebug assembleProdDebug
```

## Architecture rules

Dependencies flow in one direction: components → hooks/stores → platform → native plugin.
Shared utilities must not import UI, stores, or native code. UI code must use the typed platform
interface instead of importing a Capacitor plugin directly.

- Keep cross-cutting routes, storage keys, and runtime defaults in their shared owner module.
- Keep single-component constants local; do not create catch-all constants or utility modules.
- Prefer named, typed interfaces at runtime boundaries. Do not introduce `any`, `@ts-ignore`, or
  silent error handling.
- Use the scoped logger and never log full URLs, tunnel configuration, keys, or user data.
- Preserve persisted storage keys and provide a versioned migration when stored state changes.

## Quality policy

- Biome owns TypeScript, React, JSON, and CSS formatting/linting; Spotless owns Android sources.
- Knip findings must be fixed. An exception is allowed only for tooling that consumes a dependency
  indirectly and must be documented next to the Knip configuration.
- Knip relies on its Capacitor, PostCSS, Vite, and Vitest integrations to discover tooling entry
  points and indirect dependencies. Its single dependency exception is `tailwindcss`, which is
  imported from CSS and therefore invisible to Knip's TypeScript dependency analysis.
- ShellCheck is installed by the development environment and CI rather than npm, so Knip ignores
  that single external binary while `npm run check:scripts` still requires and executes it.
- Tests must be deterministic and must not access the public TON network. Mock native and network
  boundaries.
- Coverage thresholds are 80% for lines, statements, and functions and 75% for branches across
  stores, libraries, platform adapters, and hooks.
- Lint suppressions must be local, minimal, and include a reason. Global suppressions are not
  accepted.

## Pull requests

Keep each pull request focused and reviewable. Put mechanical formatting in a separate commit from
behavior changes. Describe storage migrations, native/API changes, manual Android checks, and
rollback considerations in the pull request template. Never commit generated builds, credentials,
signing material, or proxy configuration.
