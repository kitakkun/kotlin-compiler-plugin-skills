package com.example.hello

import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

@OptIn(ExperimentalCompilerApi::class)
class HelloCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = "com.example.hello"
    override val pluginOptions: Collection<CliOption> = emptyList()
}
