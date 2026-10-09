package com.mi.onextbox.lsp

/** Only suppress a confirmed disconnected icon; never force a stock-hidden icon visible. */
internal object DisconnectedBluetoothIconRules {
    fun shouldHide(enabled: Boolean, connected: Boolean?): Boolean = enabled && connected == false
}
