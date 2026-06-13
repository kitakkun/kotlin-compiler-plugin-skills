# Notices and Attributions

This documentation references several open-source compiler plugins and the
Kotlin compiler itself. Where the documentation quotes code, configuration,
or interface declarations from these projects, the **original copyright applies
to that quoted material** and the **original projects' Apache License 2.0**
governs its reuse.

The surrounding prose, the arrangement, and the kitakkun-authored code samples
are © 2026 kitakkun under the MIT License (see [LICENSE](LICENSE)).

If you copy code from this repository that originates from one of the upstream
projects below (rather than the prose), you must comply with **that project's
Apache License 2.0**, not this repository's MIT License.

---

## JetBrains/kotlin

- Repository: <https://github.com/JetBrains/kotlin>
- License: Apache License 2.0 (`license/LICENSE.txt` in that repository)
- NOTICE preserved per Apache 2.0 § 4(d):

  ```
  Kotlin Compiler
  Copyright 2010-2024 JetBrains s.r.o and respective authors and developers
  ```

  Source: <https://github.com/JetBrains/kotlin/blob/master/license/NOTICE.txt>

- Quoted in: most reference guides under `skills/kotlin-compiler-plugin/references/`, primarily for compiler API surface
  (`CompilerPluginRegistrar`, `IrMemberAccessExpression`, `KotlinCompilerVersion`,
  `IrFunction.parameters`, `FirScriptConfiguratorExtension`,
  `FirScriptResolutionConfigurationExtension`, `Fir2IrScriptConfiguratorExtension`,
  `FirReplSnippetConfiguratorExtension`, `FirReplSnippetResolveExtension`,
  `Fir2IrReplSnippetConfiguratorExtension`, `FirReplHistoryProvider`, etc.),
  test framework directives (`FIR_DUMP`, `DUMP_IR`, `<!DIAGNOSTIC!>` marker syntax),
  test runner classes (`AbstractKotlinCompilerTest`, `JvmBoxRunner`,
  `AbstractFirPhasedDiagnosticTest`, `AbstractFirBlackBoxCodegenTestBase`),
  the Gradle subplugin API (`KotlinCompilerPluginSupportPlugin`, `SubpluginArtifact`),
  and the scripting plugin reference implementations under `plugins/scripting/scripting-compiler/`
  (`FirScriptConfigurationExtensionImpl`, `FirScriptResolutionConfigurationExtensionImpl`,
  `Fir2IrScriptConfiguratorExtensionImpl`, `FirReplSnippetConfiguratorExtensionImpl`,
  `FirReplSnippetResolveExtensionImpl`, `Fir2IrReplSnippetConfiguratorExtensionImpl`,
  `FirReplHistoryProviderImpl`).
- Citations are pinned to tag `v2.4.0` unless noted otherwise.

## Kotlin/compiler-plugin-template

- Repository: <https://github.com/Kotlin/compiler-plugin-template>
- License: Apache License 2.0 (`LICENSE.txt` in that repository)
- Quoted in: `skills/kotlin-compiler-plugin/references/compiler-plugin-testing/{guide,EVIDENCE}.md`, primarily
  for the `compiler-plugin/build.gradle.kts` test configuration (dependency
  set, `setLibraryProperty`, `tasks.test` system properties), the abstract
  test runner classes (`AbstractJvmDiagnosticTest`, `AbstractJvmBoxTest`),
  the `ExtensionRegistrarConfigurator` services pattern, and `GenerateTests.kt`
  (the `generateTestGroupSuiteWithJUnit5` invocation).
- Citations are pinned to commit `c94e164ac8e970cbf252066c1d233d5787635c29`.

## ZacSweers/metro

- Repository: <https://github.com/ZacSweers/metro>
- License: Apache License 2.0 (`LICENSE` in that repository)
- Copyright notice from quoted source headers:

  ```
  Copyright (C) 2024-2025 Zac Sweers
  SPDX-License-Identifier: Apache-2.0
  ```

- Quoted in: `skills/kotlin-compiler-plugin/references/multi-version-kotlin-support/{guide,EVIDENCE}.md`,
  primarily for the `CompatContext` compat-shim interface and its
  `@CompatApi(since=, reason=, message=)` annotation, the dev-track-aware
  `resolveFactoryForVersion` resolver, the `compiler-compat/k*/`
  module layout, the `version-aliases.txt` and `ide-mappings.txt` files,
  and the `MetroGradleSubplugin.getPluginArtifact()` shape.
- Citations are pinned to commit `45b38b230540497af32c8d176cbf18460cb44e19`.

## Kotlin/kotlinx-rpc

- Repository: <https://github.com/Kotlin/kotlinx-rpc>
- License: Apache License 2.0 (`LICENSE` in that repository)
- Copyright notice from quoted source headers:

  ```
  Copyright 2023-2025 JetBrains s.r.o and contributors.
  Use of this source code is governed by the Apache 2.0 license.
  ```

- Quoted in: `skills/kotlin-compiler-plugin/references/multi-version-kotlin-support/{guide,EVIDENCE}.md`,
  primarily for the CSM (Compiler-Specific Modules) directive syntax
  (`//##csm`, `//##csm specific=[range]`, `//##csm default`), the
  `CsmTemplateProcessor` template engine (`matchesKotlinVersion`), the
  `FirVersionSpecificApi` interface, the
  `KotlinCompilerPluginBuilder.getPluginArtifact()` per-Kotlin-version
  coordinate shape, and the `<kotlinVersion>-<libraryVersion>` publication
  scheme.
- Citations are pinned to commit `3c3c6f0201253958d15248ffdafa138e143a4ce6`.

---

## Scope clarification

The reference guides in this repository are pedagogical: they describe and
contextualise patterns that appear in the upstream projects above. Quotations
are limited to what is necessary to make the patterns concrete and verifiable.
For each substantial quotation, the source is identified by URL and pinned
commit / tag in the corresponding `EVIDENCE.md` file or inline in `guide.md`.

This NOTICE supplements those inline pointers and exists primarily to satisfy
Apache 2.0 § 4(b) and § 4(d) attribution and notice-preservation requirements
in one place.
