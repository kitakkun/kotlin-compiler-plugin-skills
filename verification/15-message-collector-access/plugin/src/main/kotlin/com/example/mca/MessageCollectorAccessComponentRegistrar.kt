package com.example.mca

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.CliDiagnostics
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.report
import org.jetbrains.kotlin.cli.reportInfo
import org.jetbrains.kotlin.cli.reportLog
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.MessageCollectorAccess
import org.jetbrains.kotlin.config.messageCollector

// The messageCollector accessor is gated behind the MessageCollectorAccess opt-in, which is scoped
// to this function alone; the report helpers in this file compile without it.
@OptIn(MessageCollectorAccess::class)
private fun obtainMessageCollector(configuration: CompilerConfiguration): MessageCollector =
    configuration.messageCollector

@OptIn(ExperimentalCompilerApi::class)
class MessageCollectorAccessComponentRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = "com.example.mca.message-collector-access"
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        configuration.reportInfo("15-mca: reportInfo from registrar")
        configuration.reportLog("15-mca: reportLog from registrar")
        configuration.report(
            CliDiagnostics.COMPILER_PLUGIN_INITIALIZATION_WARNING,
            "15-mca: report(COMPILER_PLUGIN_INITIALIZATION_WARNING) from registrar",
        )

        val messageCollector = obtainMessageCollector(configuration)
        messageCollector.report(CompilerMessageSeverity.WARNING, "15-mca: hello from registrar")
        IrGenerationExtension.registerExtension(HelloIrGenerationExtension(messageCollector))
    }
}
