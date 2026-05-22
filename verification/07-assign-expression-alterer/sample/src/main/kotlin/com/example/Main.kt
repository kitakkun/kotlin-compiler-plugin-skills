package com.example

class Property<T> {
    private var v: T? = null
    fun assign(value: T) {
        v = value
        println("assigned: $value")
    }
    fun get(): T? = v
}

class Task {
    val input: Property<String> = Property()
}

fun main() {
    val t = Task()
    t.input = "OK"
    println("get: ${t.input.get()}")
}
