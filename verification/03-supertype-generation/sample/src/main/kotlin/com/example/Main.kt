package com.example

fun main() {
    println("--- main start ---")
    println("Foo.Companion.marked() = ${Foo.Companion.marked()}")
    val asMarker: Marker = Foo.Companion as Marker
    println("(Foo.Companion as Marker).marked() = ${asMarker.marked()}")
    println("--- main end ---")
}
