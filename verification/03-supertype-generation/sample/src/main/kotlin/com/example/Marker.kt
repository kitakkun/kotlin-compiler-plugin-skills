package com.example

interface Marker {
    fun marked(): String
}

annotation class Tagged

@Tagged
class Foo {
    companion object {
        // Plugin injects `Marker` as a supertype on this companion via FirSupertypeGenerationExtension,
        // so this method must be marked `override` to satisfy `Marker.marked()`.
        override fun marked(): String = "yes"
    }
}
