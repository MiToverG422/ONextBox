package com.mi.onextbox.lsp

internal object AppLockDelayMenuOptions {
    data class Options(val entries: Array<CharSequence>, val values: Array<CharSequence>)
    fun build(entries: Array<CharSequence>, values: Array<CharSequence>, enabled: Boolean, label: (Long) -> String): Options {
        require(entries.size == 2 && values.size == 2)
        return if (enabled) Options(entries + AppLockDelaySettings.durations.map(label),
            values + AppLockDelaySettings.durations.map(AppLockDelaySettings::token))
        else Options(entries.copyOf(), values.copyOf())
    }
    fun persist(value: String, nativeExitValue: String) =
        if (AppLockDelaySettings.fromToken(value) != null) nativeExitValue else value
}
