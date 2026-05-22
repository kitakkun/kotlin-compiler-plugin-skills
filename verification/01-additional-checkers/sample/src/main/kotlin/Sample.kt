package com.example

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class MustBeFinal

@MustBeFinal
class Ok

@MustBeFinal
open class Bad
