package com.example.assign

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class AssignFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::PropertyAssignAlterer
    }
}
