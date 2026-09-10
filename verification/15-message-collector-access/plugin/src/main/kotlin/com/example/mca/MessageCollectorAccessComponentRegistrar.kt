package com.example.mca

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.CliDiagnostics
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.report
import org.jetbrains.kotlin.cli.reportInfo
import org.jetbrains.kotlin.cli.reportLog
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.MessageCollectorAccess
import org.jetbrains.kotlin.config.messageCollector

// Claim (1): since Kotlin 2.4.20 `CompilerConfiguration.messageCollector` (and
// `CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY`) are gated behind the
// `@RequiresOptIn` marker `MessageCollectorAccess`. Removing `MessageCollectorAccess::class`
// from the `@OptIn` below must turn this file into a compile error.
@OptIn(ExperimentalCompilerApi::class, MessageCollectorAccess::class)
class MessageCollectorAccessComponentRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = "com.example.mca.message-collector-access"
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        // Claim (3): the opt-in-free helpers from org.jetbrains.kotlin.cli.
        configuration.reportInfo("15-mca: reportInfo from registrar")
        configuration.reportLog("15-mca: reportLog from registrar")
        configuration.report(
            CliDiagnostics.COMPILER_PLUGIN_INITIALIZATION_WARNING,
            "15-mca: report(COMPILER_PLUGIN_INITIALIZATION_WARNING) from registrar",
        )

        // Claim (1)/(2): direct collector access with the opt-in.
        val messageCollector = configuration.messageCollector
        messageCollector.report(CompilerMessageSeverity.WARNING, "15-mca: hello from registrar")
        IrGenerationExtension.registerExtension(HelloIrGenerationExtension(messageCollector))
    }
}
