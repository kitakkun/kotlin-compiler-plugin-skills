package com.example

annotation class Tag(val name: String, val times: Int)

@Tag(name = "greeting", times = 3)
fun tagged(): String = "plugin did not run"

fun main() {
    println(tagged())
}
