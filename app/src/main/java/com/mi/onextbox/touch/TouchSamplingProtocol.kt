package com.mi.onextbox.touch

/** Decode only Parcel hex columns, never row addresses or the ASCII preview. */
internal object TouchSamplingProtocol {
    private val word = Regex("^[0-9a-fA-F]{8}$")
    private val parcel = Regex("^(?:Result:\\s*)?Parcel\\(([\\s\\S]*)\\)\\s*$")
    private val rowAddress = Regex("^\\s*0x[0-9a-fA-F]+:\\s*")
    private val modePattern = Regex("^ *(0|[1-9][0-9]*) *, *(0|[1-9][0-9]*) *$")

    private fun words(text: String): List<Long> {
        if (text.length > 65536) return emptyList()
        val body = parcel.matchEntire(text.trim())?.groupValues?.get(1) ?: return emptyList()
        val data = mutableListOf<Long>()
        for (line in body.lineSequence()) {
            if ('\'' in line && !line.substringAfter('\'').trimEnd().endsWith('\'')) return emptyList()
            val column = rowAddress.replace(line.substringBefore('\''), "").trim()
            if (column.isEmpty()) {
                if ('\'' in line) return emptyList()
                continue
            }
            val tokens = column.split(Regex("\\s+"))
            if (tokens.any { !word.matches(it) }) return emptyList()
            data += tokens.map { it.toLong(16) }
        }
        return data
    }

    fun parcelInt(text: String): Int? {
        val data = words(text)
        if (data.size != 2 || data[0] != 0L) return null
        return data[1].toInt()
    }

    fun parcelString(text: String): String? {
        val data = words(text)
        if (data.size < 2 || data[0] != 0L) return null
        val length = data[1]
        if (length !in 0L..4096L) return null
        if (length == 0L && data.size == 2) return ""
        val payloadWords = ((length + 2) / 2).toInt()
        if (data.size != payloadWords + 2) return null
        val bytes = data.drop(2).flatMap { value ->
            (0..3).map { byte -> ((value shr (byte * 8)) and 0xff).toByte() }
        }.toByteArray()
        if (bytes.drop(length.toInt() * 2).any { it != 0.toByte() }) return null
        return String(bytes, 0, length.toInt() * 2, Charsets.UTF_16LE)
    }

    fun currentMode(reply: String): Pair<Int, Int>? {
        val text = parcelString(reply) ?: return null
        if (text.length !in 3..32) return null
        val match = modePattern.matchEntire(text) ?: return null
        val index = match.groupValues[1].toIntOrNull()?.takeIf { it in 0..255 } ?: return null
        val chip = match.groupValues[2].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        return index to chip
    }

    // A valid read can rescue data display when a vendor's capability query disagrees.
    // It is NOT sufficient evidence that writing the indexed node is supported.
    fun canDisplay(support: Int?, mode: Pair<Int, Int>?): Boolean = support == 1 || mode != null
    fun canWrite(support: Int?, mode: Pair<Int, Int>?): Boolean = support == 1 && mode != null
}
