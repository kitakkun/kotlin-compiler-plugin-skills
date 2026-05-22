package com.example

annotation class WithReceiver

@WithReceiver
fun interface Action<T> {
    fun run(receiver: T)
}

fun execute(action: Action<String>) {
    action.run("hello")
}

fun main() {
    execute { println("got: " + this) }
}
