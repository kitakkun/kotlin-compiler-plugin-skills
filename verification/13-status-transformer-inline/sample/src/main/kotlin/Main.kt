package com.example

annotation class MakeInline

@MakeInline
fun runIt(block: () -> Unit) {
    block()
}

fun findIt(): String {
    runIt { return "found" }
    return "not-found"
}

fun main() {
    println(findIt())
}
