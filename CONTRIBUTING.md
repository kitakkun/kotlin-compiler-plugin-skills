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

## When Kotlin ships a new version

The same procedure applies to a patch (2.4.0 → 2.4.10) and a minor (2.3.21 → 2.4.0); a patch usually finishes at step 1 with "no cited path changed". Suppose Kotlin 2.4.20 ships:

0. **Establish the target tag first — do not trust a local clone's current checkout.** Confirm the new version is actually released (`git ls-remote --tags https://github.com/JetBrains/kotlin 'v2.4.*'` — a local clone's tag list can be stale, and its working tree may sit on an unrelated dev build like `build-2.4.20-dev-*`) and that the artifacts are on Maven Central (`curl -sI https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-compiler-embeddable/2.4.20/ | head -1`). Then `git -C <kotlin-clone> fetch --no-tags origin tag v2.4.10 tag v2.4.20`. Read source at the exact tag with `git show v2.4.20:<path>` / `git grep … v2.4.20` / `git diff v2.4.10 v2.4.20 -- <path>` rather than relying on whatever is checked out. The "latest" is whatever JetBrains has tagged as a final release, **not** whatever the clone happens to point at.
1. **Scope the work by diffing the cited source paths between tags:**
   ```bash
   grep -rhoE 'blob/v2\.4\.10/[^)#]+' skills/kotlin-compiler-plugin/SKILL.md skills/kotlin-compiler-plugin/references/*/{EVIDENCE,guide}.md \
     | sed 's#blob/v2.4.10/##' | sort -u > /tmp/cited.txt
   git -C <kotlin-clone> diff --name-only v2.4.10 v2.4.20 -- $(cat /tmp/cited.txt | tr '\n' ' ')
   ```
   Pass the paths as a **command substitution** (`$(...)`) as shown — in zsh an unquoted `$VAR` is *not* word-split, so `-- $PATHS` silently becomes one bogus path and the diff prints nothing, which looks exactly like "no changes". (Also never name a shell variable `path` in zsh — it aliases `$PATH`.) An empty result here with thousands of upstream commits is a red flag, not a pass. Then pin `skills/kotlin-compiler-plugin/references/compiler-plugin-bootstrap/example/` to the new version and run `./gradlew --no-daemon clean :sample:run` — note every API or flag that broke.
2. For each changed cited file, read the diff and decide which reference is affected. Update its `guide.md` to reflect the new API. Add a `## Kotlin 2.4.10 → 2.4.20` section at the top of that reference's `CHANGES.md` **only** for plugin-author-visible changes — line drift and internal refactors do not warrant one. For a large diff, fan the work out per topic (one sub-agent per reference directory, each editing only its own directory) and review the combined diff afterwards.
3. **Re-pin permalinks, then hunt line drift.** Run `scripts/bump_kotlin_version.sh v2.4.10 v2.4.20` (rewrites the tag segment of every permalink in `SKILL.md` / `guide.md` / `EVIDENCE.md`; leaves `CHANGES.md`, `README.md`, `CHANGELOG.md` alone and prints the remaining literal version mentions to update by hand). Then run `scripts/check_citation_drift.sh <kotlin-clone> v2.4.10 v2.4.20` — it compares every cited line range at both tags and prints `DRIFT` (with candidate new line numbers) or `MISSING` (path moved/deleted) for each citation that needs a new anchor. Fix each flagged range, then finish with `scripts/verify_citations.sh <kotlin-clone>` (existence check + first cited line) as the final gate. `verify_citations.sh` alone is **not** sufficient: it cannot see that a file changed above the cited line.
4. Update the code-sample version pins the bump script listed (`kotlin("jvm") version`, `kotlin-compiler-embeddable`, `kotlin-compiler`, test-framework artifacts, the `kotlin.compiler` property default, the CI matrix) and the prose markers ("verified on …", "pinned to Kotlin …", "at v2.4.10 …"). Leave *event* markers alone ("removed in 2.4.0" describes when something happened, not where it was checked).
5. Re-validate by following each affected reference's `guide.md` from scratch (a fresh agent on a fresh checkout is the gold-standard test). When no reference guide was affected (typical for a patch), say so in the CHANGELOG instead of re-running `evaluation/`.
6. Bump `.claude-plugin/plugin.json` `version` per semver — typically PATCH if the changes are pure migration recipes, MINOR if a recommended approach changes substantially. The plugin's version is independent of Kotlin's.
7. Update `README.md`'s "Compatibility matrix" with a new row for the new plugin version → Kotlin range (keep the historical rows). Bump the "targets … (2.x.x)" markers in `README.md`, `SKILL.md`, and `NOTICE.md`.
8. Add an entry to root `CHANGELOG.md` describing the release.

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
