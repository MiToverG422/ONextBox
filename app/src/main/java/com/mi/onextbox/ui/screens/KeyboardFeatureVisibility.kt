package com.mi.onextbox.ui.screens

import androidx.annotation.StringRes
import com.mi.onextbox.R

internal object KeyboardFeatureVisibility {
    val emojiGroupVisible: Boolean = true

    fun isSearchEntryVisible(@StringRes title: Int): Boolean = emojiGroupVisible ||
        (title != R.string.keyboard_emoji_group && title != R.string.keyboard_flag_emoji_title)
}
