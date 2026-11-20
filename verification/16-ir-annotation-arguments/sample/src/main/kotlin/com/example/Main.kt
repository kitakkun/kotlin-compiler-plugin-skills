package com.example

annotation class Tag(val name: String, val times: Int)

@Tag(name = "greeting", times = 3)
// The plugin replaces this body; the string shows only if the plugin does not run.
fun tagged(): String = "plugin did not run"

fun main() {
    println(tagged())
}
