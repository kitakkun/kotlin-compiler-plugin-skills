# Verification 03 — `FirSupertypeGenerationExtension`

**Result: PASS**

## What was verified

A K2 compiler plugin uses `FirSupertypeGenerationExtension` to inject the `com.example.Marker` interface as a supertype on the companion object of any class annotated with `@com.example.Tagged`.

## Plugin shape

- `TaggedComponentRegistrar` — `CompilerPluginRegistrar` (`pluginId = "com.example.supertype"`, `supportsK2 = true`); registers a `FirExtensionRegistrarAdapter`.
- `TaggedExtensionRegistrar` — `FirExtensionRegistrar` that wires `+::TaggedSupertypeGenerator`.
- `TaggedSupertypeGenerator(session)` — extends `FirSupertypeGenerationExtension(session)`:
  - `registerPredicates()` registers `DeclarationPredicate.create { annotated(FqName("com.example.Tagged")) }`.
  - `needTransformSupertypes(decl)` returns true when `decl is FirRegularClass && decl.isCompanion` and the companion's containing class (looked up via `symbol.getContainingDeclaration(session) as? FirClassSymbol<*>`) matches the `@Tagged` predicate via `session.predicateBasedProvider.matches(...)`.
  - `computeAdditionalSupertypes(...)` looks up `ClassId(FqName("com.example"), Name.identifier("Marker"))` via `session.symbolProvider.getClassLikeSymbolByClassId(...)`, then returns `[MARKER_CLASS_ID.constructClassLikeType(emptyArray(), isMarkedNullable = false)]`. It short-circuits when `Marker` is already a declared supertype.

## Sample

- `interface Marker { fun marked(): String }`
- `annotation class Tagged`
- `@Tagged class Foo { companion object { override fun marked(): String = "yes" } }` — note the `override` modifier is **mandatory**: the compiler sees the injected `Marker` supertype during front-end resolution, so omitting `override` produced a `'marked' hides member of supertype 'Marker' and needs an 'override' modifier` error during a sanity-check run.
- `main()` prints `Foo.Companion.marked()` and `(Foo.Companion as Marker).marked()`.

## Run output (`../gradlew :sample:run`)

```
> Task :sample:run
--- main start ---
Foo.Companion.marked() = yes
(Foo.Companion as Marker).marked() = yes
--- main end ---

BUILD SUCCESSFUL
```

## Bytecode-level evidence

`javap -p .../com/example/Foo$Companion.class`:

```
public final class com.example.Foo$Companion implements com.example.Marker {
  ...
  public java.lang.String marked();
  ...
}
```

The injected supertype shows up in the actual `implements` list of the generated `.class` file.

## Why this proves the supertype was injected (not just declared in metadata)

The cast `Foo.Companion as Marker` is enforced at runtime by a JVM `CHECKCAST` against `com/example/Marker`. Had the plugin only added `Marker` to FIR metadata without it making it into the supertype list of the produced bytecode, the cast would have thrown `ClassCastException` at runtime — but the program completes normally and prints the second line. Combined with the `javap` output above, this confirms the supertype is materialised end-to-end (FIR → IR → JVM bytecode) and is not a metadata-only artefact.

## Re-run on Kotlin 2.4.20

**Status: PASS** (unchanged from the 2.3.21 result; no source changes were needed, only the version pins in `build.gradle.kts`).

```
$ ../gradlew --no-daemon -q clean :sample:run
--- main start ---
Foo.Companion.marked() = yes
(Foo.Companion as Marker).marked() = yes
--- main end ---
(exit code 0)
```

Validated with Kotlin 2.4.20, Gradle 9.5.0, JDK 21 on 2026-09-10.
