package com.mi.onextbox.lsp

internal enum class SettingsEntryPosition(val value: Int) {
    Disabled(0), Top(1), Middle(2), Bottom(3);
}

internal enum class SettingsEntryIconStyle(val value: Int) {
    System(0), App(1);
}

internal data class SettingsHomeEntryOptions(val position: SettingsEntryPosition, val iconStyle: SettingsEntryIconStyle) {
    val enabled: Boolean get() = position != SettingsEntryPosition.Disabled

    companion object {
        fun resolve(position: Int?, legacyEnabled: Boolean, iconStyle: Int?): SettingsHomeEntryOptions =
            SettingsHomeEntryOptions(
                if (position == null) {
                    if (legacyEnabled) SettingsEntryPosition.Top else SettingsEntryPosition.Disabled
                } else SettingsEntryPosition.entries.firstOrNull { it.value == position } ?: SettingsEntryPosition.Disabled,
                SettingsEntryIconStyle.entries.firstOrNull { it.value == iconStyle } ?: SettingsEntryIconStyle.System,
            )
    }
}
