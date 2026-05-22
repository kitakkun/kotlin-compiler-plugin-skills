package com.example.checker

import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.error0
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.psi.KtClass

object MustBeFinalDiagnostics : KtDiagnosticsContainer() {
    val MUST_BE_FINAL_OPEN by error0<KtClass>(SourceElementPositioningStrategies.MODALITY_MODIFIER)

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = MustBeFinalDefaultErrorMessages
}

object MustBeFinalDefaultErrorMessages : BaseDiagnosticRendererFactory() {
    override val MAP by KtDiagnosticFactoryToRendererMap("MustBeFinal") { map ->
        map.put(
            MustBeFinalDiagnostics.MUST_BE_FINAL_OPEN,
            "Class annotated @MustBeFinal must not be open",
        )
    }
}
