package com.mi.onextbox.lsp

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class QsPipelineCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    private val limit = 1024L * 1024
    private val cache get() = File(temporary.root, QsPipelineCache.NAME)

    private fun writeCache(valueBytes: Int, key: Int = 1, vkHeader: Int = 32, trailingBytes: Int = 0) {
        val data = ByteBuffer.allocate(136).order(ByteOrder.LITTLE_ENDIAN)
        data.putInt(0x244c4745).putInt(0).putInt(0x5f426224).putInt(3).putInt(1).putInt(0)
            .putLong(2).putInt(15)
        data.position(56)
        data.putLong(1).putLong(32)
        data.position(108)
        data.putLong(4).putLong(valueBytes.toLong()).putInt(key).putInt(vkHeader).putInt(1)
        RandomAccessFile(cache, "rw").use {
            it.write(data.array())
            it.setLength(128L + valueBytes + trailingBytes)
        }
    }

    @Test fun keepsHealthyPipelineEvenWhenTheWholeFileIsLarge() {
        writeCache(64 * 1024, trailingBytes = limit.toInt())
        assertFalse(QsPipelineCache.prepare(temporary.root, limit))
        assertTrue(cache.exists())
    }

    @Test fun preservesOriginalBytesAndDoesNotRepeatRepairOnTheNextRestart() {
        writeCache(limit.toInt())
        val previous = cache.readBytes()
        assertTrue(QsPipelineCache.prepare(temporary.root, limit))
        assertFalse(cache.exists())
        assertArrayEquals(previous, File(temporary.root, "${cache.name}.onextbox-previous").readBytes())
        writeCache(limit.toInt())
        assertFalse(QsPipelineCache.prepare(temporary.root, limit))
    }

    @Test fun ignoresOtherKeysDriverFormatsAndTruncatedEntries() {
        writeCache(limit.toInt(), key = 2)
        assertFalse(QsPipelineCache.prepare(temporary.root, limit))
        writeCache(limit.toInt(), vkHeader = 8)
        assertFalse(QsPipelineCache.prepare(temporary.root, limit))
        writeCache(limit.toInt())
        RandomAccessFile(cache, "rw").use { it.setLength(limit - 1) }
        assertFalse(QsPipelineCache.prepare(temporary.root, limit))
    }
}
