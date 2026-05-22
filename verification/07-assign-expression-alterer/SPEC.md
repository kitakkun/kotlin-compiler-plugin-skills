# Verification 07 — `FirAssignExpressionAltererExtension`

## Goal

Verify that `FirAssignExpressionAltererExtension` rewrites property assignments into method calls. Build a plugin that turns `prop = value` into `prop.assign(value)` for properties whose type is `com.example.Property<T>`.

## Sample requirements

- `class Property<T> { private var v: T? = null; fun assign(value: T) { v = value; println("assigned: $value") }; fun get(): T? = v }`
- `class Task { val input: Property<String> = Property() }`
- `fun main() { val t = Task(); t.input = "OK"; println("get: ${t.input.get()}") }`

Without the plugin, `t.input = "OK"` is a type error (`String` not assignable to `Property<String>`). With the plugin, it rewrites to `t.input.assign("OK")` and prints:

```
assigned: OK
get: OK
```

## PASS criterion

`./gradlew :sample:run` succeeds and prints both lines in order.

## Skills to consult

- `fir-assign-expression-alterer-extension`
- `compiler-plugin-bootstrap`
