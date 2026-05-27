---
name: kotlin-compiler-plugin
description: Build a Kotlin compiler plugin — scaffold a project, write K2 (FIR) frontend extensions, write IR backend transformations, test with the official infrastructure, debug what the plugin actually does, package the plugin as a Gradle plugin, and ship it across Kotlin versions. Use when (1) starting a new compiler-plugin project, (2) adding or modifying any FIR/IR extension, (3) writing custom diagnostics or synthetic declarations, (4) building a `.kts`/REPL dialect, (5) instrumenting or rewriting calls/bodies at IR time, (6) packaging via `KotlinCompilerPluginSupportPlugin`, (7) supporting multiple Kotlin compiler versions in one plugin, or (8) debugging/testing any of the above. Targets Kotlin 2.3.x. This SKILL.md is a router — read the matching `references/<topic>/guide.md` for the actual how-to, plus the sibling `EVIDENCE.md` / `CHANGES.md` when you need primary-source citations or Kotlin-version migration notes. NOT a tutorial on the Kotlin language, the IntelliJ debugger basics, JUnit basics, or Gradle plugin authoring basics.
---

# Kotlin compiler plugin

This skill bundles the how-to for every supported topic into one directory of reference guides. Load only what the current task needs.

## How to use this skill

1. Identify the goal from the table below.
2. Read `references/<topic>/guide.md` — that's the topic's full skill content.
3. If the topic has `EVIDENCE.md` (primary-source citations) or `CHANGES.md` (Kotlin-version migration notes) and you need them, Read those too. They are only present when there is something worth recording.
4. Some guides themselves point to other guides via `../<other-topic>/guide.md`. Follow them when the current task spans multiple topics.

## How to plan parallel work

For non-trivial plugin work (more than a single-file change), use a **spec-first, fan-out** workflow. Compiler plugins decompose cleanly into FIR / IR / Gradle / sample parts that are largely independent once the shared scaffolding is fixed — so wall-clock time drops a lot if you parallelise.

1. **Freeze the spec first.** Before writing code, write a short `intent.md` (or reuse the task's `SPEC.md`) capturing: feature name, user-visible API shape, what FIR does, what IR does, the sample code that exercises it, and the verification commands. One page is enough. Without this, parallel agents will redesign the API mid-flight and collide.

2. **Settle the shared bottlenecks sequentially.** These touch files every parallel branch will read, so do them in one place first:
   - `settings.gradle.kts` (every module added must be enumerated here)
   - `plugin/build.gradle.kts` `dependencies { }` block
   - `plugin/src/main/resources/META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar` and `…CommandLineProcessor`
   - The registrar's `registerExtensions(...)` body that wires every FIR/IR extension
   - The shared `PluginNames` / `PluginKey` constants

3. **Fan out the rest in parallel.** Once the bottlenecks are frozen, these branches are usually safe to run as concurrent sub-agents (use the `Agent` tool with `run_in_background: true`):
   - FIR extension body — checker logic, declaration generator, supertype generator, etc. (each FIR extension class is independent)
   - IR extension body — call rewriter, body modifier, synthetic class generator (IR is independent of FIR *unless* it fills FIR-generated stub names — in that case the FIR stub names must be in the spec)
   - `sample/` source — exercises the public API contract; depends only on the contract, not on internals
   - Test data / box-test fixtures
   - Reference-guide / README updates

   Brief each agent on exactly which subdir / file / class it owns. **Two agents must never touch the same file.** If you find yourself wanting two agents to edit the same Gradle script or registrar, that work belongs in step 2, not step 3.

4. **Integrate sequentially.** Once all sub-agents return, you (the parent) run the verification commands from step 1. Don't trust agent self-reports — actually build the plugin and the sample, compile box tests, grep the output. If a build collides on a file two agents touched, the decomposition was wrong; redo step 2 with that file pulled into the sequential phase.

When the change is small (one file, one fix, one new diagnostic on an existing checker), skip this whole flow — the planning overhead exceeds the speedup.

## Topic index

### Foundation
| Topic | When to read |
|---|---|
| `compiler-plugin-bootstrap` | Scaffolding a new Kotlin compiler plugin from scratch |
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
| `fir-assign-expression-alterer-extension` | Rewrite property assignments to an arbitrary statement (e.g. `x = v` → `x.assign(v)`) |
| `fir-function-type-kind-extension` | Declare new function-type families (e.g. `@Composable () -> Unit`) |
| `fir-function-call-refinement-extension` | Refine a call's return type at the call site by generating local declarations (data-frame schema-inference pattern, advanced/unstable) |
| `fir-scripting-extensions` | Define a `.kts`-style script dialect |
| `fir-repl-snippet-extensions` | Implement a REPL / Jupyter-style dialect with cross-snippet visibility |

### IR (backend) transformations
| Topic | What it covers |
|---|---|
| `ir-plugincontext-usage` | Symbol lookup, diagnostic reporting, and metadata registration — the foundation API for any IR extension |
| `ir-call-rewriting` | Replace function calls in user code with calls to a different function |
| `ir-body-modification` | Modify existing function bodies, including filling in stubs declared by FIR |
| `ir-synthetic-class-generation` | Synthesise new IR classes — typically the IR-side counterpart of FIR-generated declarations |

## Reading order

For someone new to compiler plugins:

1. `compiler-plugin-bootstrap` — project layout, hello-world plugin
2. `compiler-plugin-debugging` — visibility setup *before* you do anything non-trivial
3. `fir-extensions-overview` — architectural overview of K2 plugins
4. `fir-predicate-system` — used by every FIR extension
5. Pick by goal:
   - Diagnostics → `fir-additional-checkers-extension`
   - Synthesise members → `fir-declaration-generation-extension`
   - Inject supertypes → `fir-supertype-generation-extension`
   - Rewrite modifiers → `fir-status-transformer-extension`
6. `fir-session-components` — for plugins that share state across extensions
7. `ir-plugincontext-usage` — foundation for any IR work
8. Pick by goal:
   - Replace calls → `ir-call-rewriting`
   - Modify bodies → `ir-body-modification`
   - Generate classes → `ir-synthetic-class-generation`
9. `compiler-plugin-testing` — once you have non-trivial behaviour
10. `gradle-plugin-integration` — when ready to ship
11. `multi-version-kotlin-support` — only if you need it

## Source-of-truth conventions inside each reference

```
references/<topic>/
├── guide.md       # current best practice for the supported Kotlin range
├── CHANGES.md     # what changed across Kotlin versions (only when there's something to record)
└── EVIDENCE.md    # primary-source citations for non-obvious claims (file:line in JetBrains/kotlin)
```

`EVIDENCE.md` lets a reviewer (or a future Claude session) spot-check any claim in `guide.md` against the upstream Kotlin compiler source without re-grepping. Each entry pairs a claim with a `kotlin/<path>:line` citation; some include a code snippet. Not all guides have one — only those with claims a careful reader would want to verify.

When a plugin author upgrades their Kotlin compiler and their plugin stops compiling, the relevant `CHANGES.md` walks them through the API migration. Guides with no `CHANGES.md` had no API churn worth singling out.

## Compatibility

- Kotlin: targets the latest stable (currently 2.3.x).
- Gradle: 9.5.0+ recommended; 8.x mostly works for the user-facing patterns.
- JDK: 21+ for compilation. Java 25 currently exposes a Kotlin BTAPI bug — see `references/compiler-plugin-bootstrap/guide.md` for the workaround.
