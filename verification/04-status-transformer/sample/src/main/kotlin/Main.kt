package com.example

annotation class Open

@Open
class Base {
    fun greet(): String = "from Base"
}

class Sub : Base() {
    override fun greet(): String = "from Sub"
}

fun main() {
    val s: Base = Sub()
    println(s.greet())
}
