package com.example

annotation class RefineMe

interface Box<T>

@RefineMe
fun box(): Box<*> = object : Box<Any> {}

fun main() {
    val b = box()
    println(b.javaClass.simpleName)
}
