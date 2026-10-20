package com.mi.onextbox.ui.settings

/** Local profile changes must not wait behind unrelated remote preference writes. */
internal object SystemAdaptationWriteQueue {
    val writes = FeatureSettingWriteQueue()
}
