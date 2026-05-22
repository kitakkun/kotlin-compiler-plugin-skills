package com.example

@Target(AnnotationTarget.TYPE)
@Retention(AnnotationRetention.BINARY)
annotation class Positive

fun takePositive(x: @Positive Int) {
    println("ok: $x")
}

fun main() {
    val p: @Positive Int = 5
    takePositive(p)        // should compile: p carries @Positive

    val n: Int = 5
    takePositive(n)        // should fail: plain Int flowing into @Positive parameter
}
