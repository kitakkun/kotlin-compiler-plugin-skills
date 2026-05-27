# Contributing

Thanks for your interest! This plugin's quality depends on accurate, current documentation.

## Project shape

```
skills/kotlin-compiler-plugin/
├── SKILL.md                # router — orients Claude and lists every reference
└── references/<topic>/
    ├── guide.md            # current Kotlin's "how to do X" — no version-history cruft
    ├── CHANGES.md          # (only when needed) what changed for THIS reference across Kotlin versions
    └── EVIDENCE.md         # (only when useful) primary-source citations for non-obvious guide.md claims
```

`SKILL.md` is the entry point Claude loads when invoking the skill; it directs Claude to the right reference. Each `guide.md` is the actual how-to. `CHANGES.md` is read on demand when a user mentions a Kotlin upgrade. `EVIDENCE.md` is reviewer-facing — it lists each non-obvious factual claim from `guide.md` with a `kotlin/<path>:line` citation against the upstream JetBrains/kotlin source so a reviewer can spot-check without re-grepping.

When you add or change an API claim in a `guide.md`, update the corresponding entry in `EVIDENCE.md` (or add one). Reviewers will use `EVIDENCE.md` to verify your PR.

## Submitting a change

### Adding or editing a reference guide

1. Edit `skills/kotlin-compiler-plugin/references/<topic>/guide.md` with the latest-Kotlin advice. Keep version qualifiers out of the body unless they describe a current requirement (e.g., `-Xcontext-parameters` flag).
2. If you're documenting a **change from a previous Kotlin version** (deprecation, signature change, removal), add it to `skills/kotlin-compiler-plugin/references/<topic>/CHANGES.md` under the appropriate `## Kotlin X.Y → X.Z` heading. Create the file if it doesn't exist.
3. Update the `description` field in `SKILL.md`'s frontmatter if your change affects when the skill should be invoked, or the topic table inside `SKILL.md` if the reference's coverage changed.
4. Run validation (see below).

### Validating a reference change

Test that the reference still produces a working plugin. The bootstrap reference ships a co-located example:

```bash
cd skills/kotlin-compiler-plugin/references/compiler-plugin-bootstrap/example
./gradlew :sample:run
```

Should print the plugin's `MessageCollector.report` warning during compilation, then run `main()`.

For a reference that walks through a more involved pattern (FIR checker, IR transform), build a small example following only the reference's text and verify it works. The `verification/` projects were created exactly this way — each one probes a single claim of one reference.

## When Kotlin ships a new minor

Suppose Kotlin 2.4 ships:

1. Try to build `skills/kotlin-compiler-plugin/references/compiler-plugin-bootstrap/example/` against 2.4 — note every API or flag that broke.
2. For each break, decide which reference is affected. Update its `guide.md` to reflect the new API. Add a `## Kotlin 2.3 → 2.4` section at the top of that reference's `CHANGES.md`.
3. Re-validate by following each affected reference's `guide.md` from scratch (a fresh agent on a fresh checkout is the gold-standard test).
4. Bump `.claude-plugin/plugin.json` `version` per semver — typically PATCH if the changes are pure migration recipes, MINOR if a recommended approach changes substantially. The plugin's version is independent of Kotlin's.
5. Update `README.md`'s "Compatibility matrix" with a new row for the new plugin version → Kotlin range.
6. Add an entry to root `CHANGELOG.md` describing the release.

## Code samples

Code samples in `guide.md` should be **literally copy-pasteable**. The validation procedure above catches gaps where a snippet is missing imports or referencing an undefined symbol.

## Style

- All documentation content in English.
- The skill's `SKILL.md` frontmatter `description` should include negative scoping ("NOT for X — see Y reference"). Each `guide.md`'s own frontmatter follows the same convention so it stays useful as a standalone document.
- Cite Kotlin compiler source paths (`kotlin/compiler/...`) when introducing an API. Use repo-relative paths only — no absolute filesystem paths.
- When in doubt about an API's current shape, check `https://github.com/JetBrains/kotlin` directly.

## Reporting issues

Please include:
- Kotlin version
- Gradle version
- JDK version
- Which reference guide you were following
- What you expected vs what happened
