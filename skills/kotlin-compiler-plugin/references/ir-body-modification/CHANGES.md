# Changes affecting this skill

API migrations relevant to modifying function bodies in IR. This skill targets the **current stable Kotlin** (2.4.0).

## Kotlin 2.3 → 2.4: `IrFunction` parameter accessors removed / read-only

- `IrFunction.valueParameters` — **removed** (no deprecated alias). Use `parameters` and filter by `kind` (`parameters.filter { it.kind == IrParameterKind.Regular }` for the old `valueParameters` semantics).
- `IrFunction.extensionReceiverParameter` — **removed**. Find it via `parameters.singleOrNull { it.kind == IrParameterKind.ExtensionReceiver }`.
- `IrFunction.dispatchReceiverParameter` — **survives but is now read-only** (`val`, was `var` in 2.3.x). Reading is fine; code that *assigned* it must now mutate `parameters` (e.g. rebuild the list) instead.

Both removed members were error-level `@DeprecatedForRemovalCompilerApi` through 2.3.x, so plugins that already migrated to `parameters` are unaffected.

## Kotlin 2.2 → 2.3

### `IrFunction.valueParameters` removed; `parameters` is the unified accessor

Older versions exposed:

```kotlin
val IrFunction.dispatchReceiverParameter: IrValueParameter?   // still exists, derived
val IrFunction.extensionReceiverParameter: IrValueParameter?
val IrFunction.valueParameters: List<IrValueParameter>
```

Kotlin 2.3.x **deprecates** `valueParameters`, `extensionReceiverParameter`, and `dispatchReceiverParameter` on `IrFunction` (all marked `@DeprecatedForRemovalCompilerApi(_2_1_20)` at v2.3.21 — see `IrFunction.kt:91, 108, 200`) in favour of a single unified list:

```kotlin
val IrFunction.parameters: List<IrValueParameter>
```

Each entry carries `kind: IrParameterKind` (`DispatchReceiver`, `Context`, `ExtensionReceiver`, `Regular`). The deprecated properties still compile (with the relevant opt-in) but are scheduled for removal in a later patch; new code should use `parameters` exclusively.

`dispatchReceiverParameter` survives as a derived getter for convenience.

**Migration**:

```kotlin
// Before
function.valueParameters.forEach { param -> ... }
val ext = function.extensionReceiverParameter

// After
function.parameters.filter { it.kind == IrParameterKind.Regular }.forEach { ... }
val ext = function.parameters.firstOrNull { it.kind == IrParameterKind.ExtensionReceiver }
```

### `IrElementTransformerVoidWithContext` for `visitFunctionNew`

`visitFunctionNew` (which dispatches to `visitConstructor`/`visitSimpleFunction`) is on `IrElementTransformerVoidWithContext` only — **not** on plain `IrElementTransformerVoid`.

**Migration**: when you want `visitFunctionNew`, switch to `IrElementTransformerVoidWithContext` (in `org.jetbrains.kotlin.backend.common`). On plain `IrElementTransformerVoid`, override `visitFunction` instead.

### `IrCall.dispatchReceiver` setter deprecated

(Same change as in [`ir-call-rewriting`](../ir-call-rewriting/guide.md) — see its CHANGES.md for full context.) When inserting a call into a body, set `arguments[0]` directly rather than `dispatchReceiver = ...`.

## Kotlin 2.0 → 2.1.20 deprecations still encountered

### `irGet(field, receiver)` does not exist

The correct helper for a field load is `irGetField(receiver, field)`. Some old samples show `irGet(field, receiver)` as if it were a single-call shortcut — that overload doesn't exist on `IrBuilder`.

### `irTemporary(value, name)` parameter is `nameHint`, not `name`

Cosmetic but caught by the compiler:

```kotlin
val tmp = irTemporary(value, nameHint = "myTemp")
```
