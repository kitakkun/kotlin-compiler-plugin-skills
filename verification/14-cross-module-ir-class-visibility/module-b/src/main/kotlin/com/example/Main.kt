package com.example

fun main() {
    // All three declarations below are IR-generated in module-a and reached module-b only
    // through module-a's metadata: the constructor, the `value` property, and `describe()`.
    val holder = FooHolder("x")
    println("value=${holder.value} describe=${holder.describe()}")
    // Optional extra: an IR-generated class nested inside the source-declared `Foo`.
    println(Foo.Nested().ping())
}
