package com.mi.onextbox.lsp

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** No Android dependencies: geometry and fallback policy can be tested on the JVM. */
internal object SmallWindowRules {
    fun isSmallWindowTask(mode: Int, scenario: Int, embedded: Boolean, caption: Boolean): Boolean =
        mode != 120 && (mode == 100 || (scenario == 1 && !embedded && caption))

    fun isLandscapeOrientation(orientation: Int): Boolean = orientation in setOf(0, 6, 8, 11)

    /** Task ownership is per UID; never affect a visible non-stashed sibling of that UID. */
    fun exclusiveStashedUids(stashed: Set<Int>, visibleOther: Set<Int>): Set<Int> =
        stashed.filterTo(mutableSetOf()) { it >= 0 && it % 100000 >= 10000 && it !in visibleOther }

    fun landscapeRatio(width: Int, height: Int): Float? {
        if (width <= 0 || height <= 0) return null
        return min(width, height).toFloat() / max(width, height)
    }

    /** Widths are in dp and ratio is height/width, as in the native flexible-window table. */
    fun largerWidth(original: Int, minimum: Int, ratio: Float, width: Float, height: Float): Int {
        if (original <= 0 || minimum <= 0 || ratio <= 0f || !ratio.isFinite() ||
            !width.isFinite() || !height.isFinite() || width <= 0 || height <= 96f
        ) return original
        val limit = min(width * 0.92f, (height - 96f) / ratio).toInt()
        if (limit < max(original, minimum)) return original
        return max(original, min((original * 1.15f).toInt(), limit))
    }

    fun shouldMuteMedia(uid: Int, usage: Int, exclusiveStashed: Set<Int>): Boolean =
        uid in exclusiveStashed && (usage == 1 || usage == 14) // media / game, never calls or alarms

    data class MediaChanges(val discard: Set<Int>, val restore: Set<Int>, val mute: Set<Int>)

    /** Playback IDs may be reused. Cleanup must never touch a replacement player's state. */
    fun mediaChanges(owned: Map<Int, Any>, live: Map<Int, Any>, desired: Set<Int>): MediaChanges {
        val discard = owned.keys.filterTo(mutableSetOf()) { live[it] !== owned[it] }
        val restore = owned.keys.filterTo(mutableSetOf()) { it !in discard && it !in desired }
        val mute = desired.filterTo(mutableSetOf()) { it in live && live[it] !== owned[it] }
        return MediaChanges(discard, restore, mute)
    }

    fun showWhiteBar(
        whiteBar: Boolean,
        mode: Int,
        nextMode: Int,
        interacting: Boolean,
        windowCount: Int,
    ): Boolean = whiteBar && windowCount > 0 && !interacting &&
        ((mode == 4 && nextMode == 8) || (nextMode == 4 && (mode == 2 || mode == 4)))

    /** OEM uses this value as collection capacity too, so never return a huge fake limit. */
    fun growingWindowCapacity(original: Int, currentCount: Int, spare: Int = 2): Int {
        if (original <= 0 || currentCount < 0 || spare <= 0 ||
            currentCount.toLong() + spare >= Int.MAX_VALUE) return original
        return max(original, currentCount + spare)
    }

    /** Only this window policy is removed, never thermal or unrelated foreground policies. */
    fun removeFrameRateLimit(unlimited: Boolean, keepRunning: Boolean, smallWindowProcess: Boolean,
                             exclusivelyStashed: Boolean, highThermal: Boolean): Boolean =
        !highThermal && ((unlimited && smallWindowProcess) || (keepRunning && exclusivelyStashed))

    fun barProgress(value: Float): Float = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f

    /** Scale time by the remaining distance, so reversing mid-fade never restarts a full animation. */
    fun barTransitionDuration(from: Float, to: Float): Long =
        (200f * kotlin.math.abs(barProgress(to) - barProgress(from))).roundToLong()

    fun safeInset(original: Int, requested: Int, screenHeight: Int, handleHeight: Int): Int {
        if (original < 0 || requested < 0 || handleHeight <= 0 || screenHeight <= handleHeight) return original
        // Leave a non-empty movement range; never weaken a larger native safety boundary.
        val maximum = (screenHeight - handleHeight - 1) / 2
        return max(original, requested.coerceAtMost(maximum))
    }

    data class BarBounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

    fun barBounds(
        width: Float,
        height: Float,
        hiddenWidth: Float,
        density: Float,
        leftSide: Boolean,
    ): BarBounds? {
        if (!listOf(width, height, hiddenWidth, density).all { it.isFinite() } ||
            width <= 0f || height <= 0f || density <= 0f || hiddenWidth < 0f || hiddenWidth >= width
        ) return null
        val visibleWidth = width - hiddenWidth
        val barWidth = min(4f * density, visibleWidth / 2f)
        val gap = min(2f * density, (visibleWidth - barWidth) / 2f)
        val barHeight = min(32f * density, height * 0.8f)
        val x = if (leftSide) hiddenWidth + gap else visibleWidth - gap - barWidth
        val y = (height - barHeight) / 2f
        return BarBounds(x, y, x + barWidth, y + barHeight)
    }
}
