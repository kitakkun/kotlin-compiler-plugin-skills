package com.example

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class DslContext

class Dsl

fun Dsl.greet(): String = "from Dsl"
