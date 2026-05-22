package com.example.session

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class TrackedExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::TrackedRegistry
        +::TrackedChecker
        +::TrackedOpener
        registerDiagnosticContainers(TrackedDiagnostics)
    }
}
