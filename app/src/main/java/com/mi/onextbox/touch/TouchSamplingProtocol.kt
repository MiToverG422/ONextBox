package com.mi.onextbox.touch

/** Decode only Parcel hex columns, never row addresses or the ASCII preview. */
internal object TouchSamplingProtocol {
    private val word = Regex("^[0-9a-fA-F]{8}$")

    private fun words(text: String): List<Long> {
        if (!text.contains("Parcel(")) return emptyList()
        return text.substringAfter("Parcel(").lineSequence().flatMap { line ->
            line.substringBefore('\'').replace(Regex("^\\s*0x[0-9a-fA-F]+:"), "")
                .substringBefore(')').trim().split(Regex("\\s+"))
                .asSequence().filter { word.matches(it) }.map { it.toLong(16) }
        }.toList()
    }

    fun parcelInt(text: String): Int? {
        val data = words(text)
        if (data.firstOrNull() != 0L) return null // AIDL exception, not a node value.
        return data.getOrNull(1)?.toInt()
    }

    fun parcelString(text: String): String? {
        val data = words(text)
        if (data.size < 2 || data[0] != 0L) return null
        val length = data[1]
        if (length !in 0L..4096L) return null
        val bytes = data.drop(2).flatMap { value ->
            (0..3).map { byte -> ((value shr (byte * 8)) and 0xff).toByte() }
        }.toByteArray()
        if (bytes.size < length * 2) return null
        return String(bytes, 0, length.toInt() * 2, Charsets.UTF_16LE)
    }

    fun currentMode(reply: String): Pair<Int, Int>? {
        val text = parcelString(reply)?.trim() ?: return null
        val match = Regex("^(\\d+)\\s*,\\s*(\\d+)\\s*$").matchEntire(text) ?: return null
        val index = match.groupValues[1].toIntOrNull() ?: return null
        val chip = match.groupValues[2].toIntOrNull() ?: return null
        return index to chip
    }

    // A valid read can rescue data display when a vendor's capability query disagrees.
    // It is NOT sufficient evidence that writing the indexed node is supported.
    fun canDisplay(support: Int?, mode: Pair<Int, Int>?): Boolean = support == 1 || mode != null
    fun canWrite(support: Int?, mode: Pair<Int, Int>?): Boolean = support == 1 && mode != null
}
