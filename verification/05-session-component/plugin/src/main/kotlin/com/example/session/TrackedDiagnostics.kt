package com.example.session

import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.warning0
import org.jetbrains.kotlin.psi.KtClass

object TrackedDiagnostics : KtDiagnosticsContainer() {
    val TRACKED_USAGE by warning0<KtClass>(SourceElementPositioningStrategies.NAME_IDENTIFIER)

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = TrackedDefaultErrorMessages
}

object TrackedDefaultErrorMessages : BaseDiagnosticRendererFactory() {
    override val MAP by KtDiagnosticFactoryToRendererMap("Tracked") { map ->
        map.put(
            TrackedDiagnostics.TRACKED_USAGE,
            "Class is registered with the @Tracked session component",
        )
    }
}
