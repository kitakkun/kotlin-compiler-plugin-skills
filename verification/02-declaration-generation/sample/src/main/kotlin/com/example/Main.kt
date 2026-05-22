package com.example

annotation class WithCompanion

@WithCompanion
class Foo

fun main() {
    println(Foo.greet())
}
