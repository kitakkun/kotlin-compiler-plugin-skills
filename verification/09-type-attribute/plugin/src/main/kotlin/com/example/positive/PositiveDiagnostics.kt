package com.example.positive

import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.error0
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.psi.KtElement

object PositiveDiagnostics : KtDiagnosticsContainer() {
    val EXPECTED_POSITIVE_INT by error0<KtElement>()

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = PositiveDiagnosticRenderers
}

object PositiveDiagnosticRenderers : BaseDiagnosticRendererFactory() {
    override val MAP by KtDiagnosticFactoryToRendererMap("PositiveAttribute") { map ->
        map.put(
            PositiveDiagnostics.EXPECTED_POSITIVE_INT,
            "Expected an argument of type @Positive Int, but got a plain Int",
        )
    }
}
