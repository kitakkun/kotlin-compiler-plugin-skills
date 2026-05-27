# Kotlin Compiler Plugin Skills

A Claude Code plugin shipping a single skill, `kotlin-compiler-plugin`, that bundles **24 reference guides** covering Kotlin compiler plugin development. Use it when building K2 (FIR) frontend extensions, IR backend transformations, custom diagnostics, synthetic declarations, scripting / REPL dialects, or shipping a compiler plugin via Gradle.

Every API claim is cross-referenced against the [Kotlin compiler source](https://github.com/JetBrains/kotlin) (2.3.x).

## Installation

In Claude Code, register this repo as a plugin marketplace, then install the plugin from it:

```
/plugin marketplace add kitakkun/kotlin-compiler-plugin-skills
/plugin install kotlin-compiler-plugin-skills@kotlin-compiler-plugin-skills
```

The single `kotlin-compiler-plugin` skill is auto-discovered after install; list it via `/plugin`. Its router `SKILL.md` lives at `skills/kotlin-compiler-plugin/SKILL.md` and the per-topic guides under `skills/kotlin-compiler-plugin/references/<topic>/guide.md`. The router triggers on any Kotlin-compiler-plugin task and Claude reads only the references the task actually needs.

Install footprint: the full repo is copied to `~/.claude/plugins/cache/` (a few MB). Only the skill's `SKILL.md` is auto-loaded into Claude's context — each reference's `guide.md` is read on demand, and the bundled `verification/` (13 working reference plugins) and `evaluation/` (6 benchmark tasks) sit on disk for you to inspect locally and never enter the token budget.

To uninstall: `/plugin uninstall kotlin-compiler-plugin-skills@kotlin-compiler-plugin-skills`.

## What's included

The plugin ships one skill at `skills/kotlin-compiler-plugin/`. Its `references/` directory contains the 24 topic guides below.

### Foundation
| Topic | When to read |
|---|---|
| `compiler-plugin-bootstrap` | Scaffolding a new Kotlin compiler plugin project from scratch |
| `compiler-plugin-debugging` | Setting up `MessageCollector`, IR dumps, debugger attach |
| `compiler-plugin-testing` | Writing diagnostic / IR-box tests with the official infrastructure |
| `gradle-plugin-integration` | Packaging via `KotlinCompilerPluginSupportPlugin` for distribution |
| `multi-version-kotlin-support` | Supporting multiple Kotlin compiler versions in one plugin |

### FIR (K2 frontend) extensions
| Topic | What it covers |
|---|---|
| `fir-extensions-overview` | Architecture, the 18 extension points, FirSession lifecycle, how to choose |
| `fir-predicate-system` | Declarative annotation matching DSL (`annotated`, `parentAnnotated`, etc.) |
| `fir-additional-checkers-extension` | Emit custom compile-time diagnostics (warnings/errors) on user code |
| `fir-declaration-generation-extension` | Synthesise classes/functions/properties/constructors visible to source |
| `fir-supertype-generation-extension` | Inject supertypes (interfaces, base classes) onto existing classes |
| `fir-status-transformer-extension` | Modify modifiers on existing declarations (visibility, modality, inline, etc.) |
| `fir-expression-resolution-extension` | Inject implicit extension receivers into call resolution |
| `fir-session-components` | Share computed state across FIR extensions |
| `fir-type-attribute-extension` | Attach plugin-defined attributes to types (e.g. `@Positive Int`) so they participate in inference and metadata |
| `fir-sam-conversion-transformer-extension` | Customise SAM conversion — e.g. promote first parameter to a receiver (sam-with-receiver pattern) |
| `fir-assign-expression-alterer-extension` | Rewrite property assignments to an arbitrary statement (e.g. `x = v` → `x.assign(v)` — the pattern Gradle build scripts use via the JetBrains-maintained `kotlin-assignment` plugin) |
| `fir-function-type-kind-extension` | Declare new function-type families (e.g. `@Composable () -> Unit`) |
| `fir-function-call-refinement-extension` | Refine a call's return type at the call site by generating local declarations (data-frame schema-inference pattern, advanced/unstable) |
| `fir-scripting-extensions` | Define a `.kts`-style script dialect — `FirScriptConfiguratorExtension`, `FirScriptResolutionConfigurationExtension`, `Fir2IrScriptConfiguratorExtension` |
| `fir-repl-snippet-extensions` | Implement a REPL / Jupyter-style dialect with cross-snippet visibility — `FirReplSnippetConfiguratorExtension`, `FirReplSnippetResolveExtension`, `Fir2IrReplSnippetConfiguratorExtension`, `FirReplHistoryProvider` |

### IR (backend) transformations
| Topic | What it covers |
|---|---|
| `ir-plugincontext-usage` | Symbol lookup, diagnostic reporting, and metadata registration — the foundation API for any IR extension |
| `ir-call-rewriting` | Replace function calls in user code with calls to a different function |
| `ir-body-modification` | Modify existing function bodies, including filling in stubs declared by FIR |
| `ir-synthetic-class-generation` | Synthesise new IR classes — typically the IR-side counterpart of FIR-generated declarations |

## Reading order

For someone new to compiler plugins:

1. **`compiler-plugin-bootstrap`** — project layout, hello-world plugin
2. **`compiler-plugin-debugging`** — visibility setup *before* you do anything non-trivial
3. **`fir-extensions-overview`** — architectural overview of K2 plugins
4. **`fir-predicate-system`** — used by every FIR extension
5. Pick by goal:
   - Diagnostics → `fir-additional-checkers-extension`
   - Synthesise members → `fir-declaration-generation-extension`
   - Inject supertypes → `fir-supertype-generation-extension`
   - Rewrite modifiers → `fir-status-transformer-extension`
6. **`fir-session-components`** — for plugins that share state across extensions
7. **`ir-plugincontext-usage`** — foundation for any IR work
8. Pick by goal:
   - Replace calls → `ir-call-rewriting`
   - Modify bodies → `ir-body-modification`
   - Generate classes → `ir-synthetic-class-generation`
9. **`compiler-plugin-testing`** — once you have non-trivial behaviour
10. **`gradle-plugin-integration`** — when ready to ship
11. **`multi-version-kotlin-support`** — only if you need it

## Repo layout

The repo has three top-level directories of working Gradle projects, each with a distinct role:

| Directory | Purpose | Style |
|---|---|---|
| `verification/` | Fine-grained probes — one project per claim in the reference guides (one per row of the "How to choose" table in `fir-extensions-overview`, plus cross-cutting probes for cross-module IR visibility and alternate status-transformer slots) | 13 self-contained plugin + sample setups; each ships a `SPEC.md` and `RESULT.md` |
| `evaluation/` | End-to-end agent benchmarks — fixed implementation tasks of increasing complexity that measure how well a fresh Claude agent can produce a working plugin from the skill alone | 6 tasks; each ships a `SPEC.md` and `RESULT.md`; see `evaluation/README.md` for rubric |

The minimal didactic example for the bootstrap guide lives at `skills/kotlin-compiler-plugin/references/compiler-plugin-bootstrap/example/` (`hello-plugin`) — co-located with the guide that uses it.

When re-running evaluations or verifications you need to keep the agent honest, use the sandbox runners in `scripts/`:

```
scripts/run-evaluation.sh 03-high-trace-plugin
scripts/run-verification.sh 04-status-transformer
```

Each creates a tempdir containing only the task's `SPEC.md`. The agent works there, with no spatial access to other answer keys. (Caveat: tempdir isolation prevents *accidental* peeking; an agent that resolves absolute paths to this repo can still bypass it. Run Claude Code with restricted permissions for a stricter trust boundary.)

## Compatibility

- **Kotlin**: targets the **latest stable** (currently 2.3.x). Reference guides are written assuming the current version; for upgrading from older Kotlin compilers see the per-topic `CHANGES.md`.
- **Gradle**: 9.5.0+ recommended; 8.x mostly works for the user-facing patterns
- **JDK**: 21+ for compilation. Java 25 currently exposes a Kotlin BTAPI bug — see `skills/kotlin-compiler-plugin/references/compiler-plugin-bootstrap/guide.md` for the workaround.

## Versioning policy

The plugin uses standard [semver](https://semver.org/) for its own version, **independent of the Kotlin compiler version**:

- **MAJOR** — breaking change to skill structure, install procedure, or naming
- **MINOR** — new reference guides added, or substantial rewrites that change a guide's recommended approach
- **PATCH** — fixes, clarifications, additional gotchas, code-sample corrections

Kotlin compatibility is tracked separately. Each release validates against a specific Kotlin version range; the **Compatibility matrix** below maps plugin versions to the Kotlin versions they were tested against.

### Source-of-truth conventions

```
skills/
└── kotlin-compiler-plugin/
    ├── SKILL.md           # router — orients Claude and lists every reference
    └── references/
        ├── compiler-plugin-bootstrap/
        │   ├── guide.md       # current best practice for the supported Kotlin range
        │   ├── CHANGES.md     # what changed across Kotlin versions (only present when there's something to record)
        │   └── EVIDENCE.md    # primary-source citations for non-obvious claims (file:line in JetBrains/kotlin)
        ├── ...
```

`EVIDENCE.md` lets a reviewer (or a future Claude session) spot-check any claim in `guide.md` against the upstream Kotlin compiler source without re-grepping. Each entry pairs a claim with a `kotlin/<path>:line` citation; some include a code snippet. Not all references have one — only those with claims a careful reader would want to verify.

When a plugin author upgrades their Kotlin compiler and their plugin stops compiling, the relevant `CHANGES.md` walks them through the API migration. References with no `CHANGES.md` had no API churn worth singling out.

When Kotlin ships a new minor (e.g. 2.4):

1. References are validated against the new Kotlin; broken ones updated.
2. Each affected `CHANGES.md` gains a `## Kotlin 2.3 → 2.4` section.
3. A new plugin release is cut — the version bump follows semver normally (PATCH if just docs, MINOR if a recommended approach changes).
4. The Compatibility matrix in this README is updated.

For plugin authors writing code that targets multiple Kotlin compiler versions, see the `multi-version-kotlin-support` reference guide.

### Compatibility matrix

| Plugin version | Validated against Kotlin |
|---|---|
| 0.2.0 | 2.3.21 |
| 0.1.1 | 2.3.21 |
| 0.1.0 | 2.3.21 |

## Contributing

Issues and pull requests welcome. The skill's `SKILL.md` carries a YAML frontmatter `description` field that controls when Claude invokes the skill, and each reference's `guide.md` describes its own scope; please keep changes consistent with the existing tone (concrete, with code samples, with negative scoping).

## License

MIT — see [`LICENSE`](LICENSE).

The reference guides quote and reference code from several Apache-2.0 projects
(JetBrains/kotlin, Kotlin/compiler-plugin-template, ZacSweers/metro,
Kotlin/kotlinx-rpc). Original copyright applies to those quotations and the
quoted material remains under Apache-2.0; see [`NOTICE.md`](NOTICE.md) for the
consolidated attribution.
