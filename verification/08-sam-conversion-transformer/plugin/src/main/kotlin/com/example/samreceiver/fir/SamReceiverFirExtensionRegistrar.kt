package com.example.samreceiver.fir

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class SamReceiverFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::WithReceiverSamConversionTransformer
    }
}
