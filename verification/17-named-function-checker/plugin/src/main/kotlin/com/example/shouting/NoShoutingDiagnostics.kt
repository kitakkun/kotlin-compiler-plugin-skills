package com.example.shouting

import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.error1
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers
import org.jetbrains.kotlin.psi.KtNamedFunction

object NoShoutingDiagnostics : KtDiagnosticsContainer() {
    val NO_SHOUTING by error1<KtNamedFunction, String>(SourceElementPositioningStrategies.NAME_IDENTIFIER)

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = NoShoutingDefaultErrorMessages
}

object NoShoutingDefaultErrorMessages : BaseDiagnosticRendererFactory() {
    override val MAP by KtDiagnosticFactoryToRendererMap("NoShouting") { map ->
        map.put(
            NoShoutingDiagnostics.NO_SHOUTING,
            "Function name ''{0}'' must not be all upper-case",
            // The message is a java.text.MessageFormat pattern, so the quotes are doubled to render
            // as single quotes.
            CommonRenderers.STRING,
        )
    }
}
