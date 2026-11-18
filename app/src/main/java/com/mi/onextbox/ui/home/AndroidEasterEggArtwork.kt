package com.mi.onextbox.ui.home

import androidx.annotation.DrawableRes
import com.mi.onextbox.R

@DrawableRes
internal fun androidEasterEggArtwork(sdkInt: Int): Int? = when (sdkInt) {
    35 -> R.drawable.ic_android_easter_15
    36 -> R.drawable.ic_android_easter_16
    37 -> R.drawable.ic_android_easter_17
    else -> null
}
