package com.mi.onextbox.ui.common

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

enum class AppLogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}

@Immutable
data class AppLogEntry(
    val id: Long,
    val timestampMs: Long,
    val level: AppLogLevel,
    val tag: String,
    val message: String
) {
    fun toLine(zoneId: ZoneId = ZoneId.systemDefault()): String {
        val instant = Instant.ofEpochMilli(timestampMs)
        val time = AppLogStore.timeFormatter.format(instant.atZone(zoneId))
        return "[$time] [${level.name}] [$tag] $message"
    }
}

object AppLogStore {
    private const val MAX_ENTRIES = 500
    private const val MAX_PERSISTED_BYTES = 512 * 1024L
    private const val PERSISTENCE_FLUSH_DELAY_MS = 100L
    private const val LOG_DIR_NAME = "logs"
    private const val RUNTIME_LOG_FILE = "onextbox-runtime.log"

    private val lock = Any()
    private val persistenceLock = Any()
    private val persistenceFileLock = Any()
    private val persistenceExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "ONextBox-LogWriter").apply { isDaemon = true }
    }
    private val pendingPersistenceCommands = ArrayDeque<PersistenceCommand>()
    private var persistenceFlushScheduled = false
    @Volatile
    private var appContext: Context? = null
    @Volatile
    private var initialized = false

    internal val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private val nextId = AtomicLong(1L)
    @Volatile
    private var entriesSnapshot: List<AppLogEntry> = emptyList()

    private sealed interface PersistenceCommand {
        data class Append(val entries: List<AppLogEntry>) : PersistenceCommand
        data class Rewrite(val entries: List<AppLogEntry>) : PersistenceCommand
    }

    fun initialize(context: Context) {
        val applicationContext = context.applicationContext
        synchronized(lock) {
            appContext = applicationContext
            if (initialized) return
            initialized = true

            val loaded = loadPersistentEntries(applicationContext)
            if (loaded.isNotEmpty()) {
                val retained = loaded
                    .filterNot { it.tag.startsWith(LEGACY_HOOK_TAG_PREFIX) }
                    .takeLast(MAX_ENTRIES)
                entriesSnapshot = retained
                nextId.set((retained.maxOfOrNull { it.id } ?: 0L) + 1L)
                if (retained.size != loaded.size) enqueueRewrite()
            }
        }
    }

    fun d(tag: String, message: String) = append(AppLogLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = append(AppLogLevel.INFO, tag, message)
    fun w(tag: String, message: String) = append(AppLogLevel.WARN, tag, message)
    fun e(tag: String, message: String) = append(AppLogLevel.ERROR, tag, message)

    fun removeByTagPrefix(prefix: String) {
        synchronized(lock) {
            entriesSnapshot = entriesSnapshot.filterNot { entry -> entry.tag.startsWith(prefix) }
            enqueueRewrite()
        }
    }

    private fun append(
        level: AppLogLevel,
        tag: String,
        message: String,
        mirrorToLogcat: Boolean = true,
        timestampMs: Long = System.currentTimeMillis(),
    ) {
        appendEntries(
            newEntries = listOf(
                createEntry(
                    level = level,
                    tag = tag,
                    message = message,
                    timestampMs = timestampMs,
                )
            ),
            mirrorToLogcat = mirrorToLogcat,
        )
    }

    private fun createEntry(
        level: AppLogLevel,
        tag: String,
        message: String,
        timestampMs: Long,
    ): AppLogEntry = AppLogEntry(
        id = synchronized(lock) { nextId.getAndIncrement() },
        timestampMs = timestampMs,
        level = level,
        tag = tag,
        message = sanitizeMessage(message),
    )

    private fun appendEntries(newEntries: List<AppLogEntry>, mirrorToLogcat: Boolean) {
        if (newEntries.isEmpty()) return
        synchronized(lock) {
            val merged = entriesSnapshot + newEntries
            entriesSnapshot = if (merged.size <= MAX_ENTRIES) merged else merged.takeLast(MAX_ENTRIES)
            enqueuePersistence(PersistenceCommand.Append(newEntries))
        }
        if (mirrorToLogcat) {
            newEntries.forEach(::writeAndroidLog)
        }
    }

    private fun writeAndroidLog(entry: AppLogEntry) {
        val line = "[${entry.tag}] ${entry.message}"
        when (entry.level) {
            AppLogLevel.DEBUG -> Log.d("ONextBox", line)
            AppLogLevel.INFO -> Log.i("ONextBox", line)
            AppLogLevel.WARN -> Log.w("ONextBox", line)
            AppLogLevel.ERROR -> Log.e("ONextBox", line)
        }
    }

    private fun sanitizeMessage(message: String): String {
        return message
            .replace("\r", "")
            .replace("\n", "\\n")
            .trim()
            .ifBlank { "(empty)" }
    }

    private fun enqueueRewrite() {
        enqueuePersistence(
            command = PersistenceCommand.Rewrite(entriesSnapshot),
            replacePending = true,
            immediate = true,
        )
    }

    private fun enqueuePersistence(
        command: PersistenceCommand,
        replacePending: Boolean = false,
        immediate: Boolean = false,
    ) {
        if (appContext == null) return
        var scheduleDelayedFlush = false
        synchronized(persistenceLock) {
            if (replacePending) pendingPersistenceCommands.clear()
            pendingPersistenceCommands.addLast(command)
            if (!persistenceFlushScheduled) {
                persistenceFlushScheduled = true
                scheduleDelayedFlush = !immediate
            }
        }
        when {
            immediate -> persistenceExecutor.execute(::drainPersistenceQueue)
            scheduleDelayedFlush -> persistenceExecutor.schedule(
                ::drainPersistenceQueue,
                PERSISTENCE_FLUSH_DELAY_MS,
                TimeUnit.MILLISECONDS,
            )
        }
    }

    private fun drainPersistenceQueue() {
        val context = appContext ?: return
        val commands = synchronized(persistenceLock) {
            persistenceFlushScheduled = false
            buildList {
                while (pendingPersistenceCommands.isNotEmpty()) {
                    add(pendingPersistenceCommands.removeFirst())
                }
            }
        }
        if (commands.isEmpty()) return
        runCatching {
            synchronized(persistenceFileLock) {
                persistCommands(context, commands)
            }
        }.onFailure { error ->
            Log.w("ONextBox", "Log persistence failed", error)
        }
        val needsAnotherFlush = synchronized(persistenceLock) {
            pendingPersistenceCommands.isNotEmpty() && !persistenceFlushScheduled
        }
        if (needsAnotherFlush) {
            enqueuePersistenceFlush()
        }
    }

    private fun enqueuePersistenceFlush() {
        val shouldSchedule = synchronized(persistenceLock) {
            if (persistenceFlushScheduled || pendingPersistenceCommands.isEmpty()) {
                false
            } else {
                persistenceFlushScheduled = true
                true
            }
        }
        if (shouldSchedule) {
            persistenceExecutor.schedule(
                ::drainPersistenceQueue,
                PERSISTENCE_FLUSH_DELAY_MS,
                TimeUnit.MILLISECONDS,
            )
        }
    }

    private fun persistCommands(context: Context, commands: List<PersistenceCommand>) {
        val appendBatch = ArrayList<AppLogEntry>()

        fun flushAppendBatch() {
            if (appendBatch.isEmpty()) return
            appendEntriesToFile(context, appendBatch)
            appendBatch.clear()
        }

        commands.forEach { command ->
            when (command) {
                is PersistenceCommand.Append -> appendBatch.addAll(command.entries)
                is PersistenceCommand.Rewrite -> {
                    flushAppendBatch()
                    rewritePersistentFile(context, command.entries)
                }
            }
        }
        flushAppendBatch()
    }

    private fun appendEntriesToFile(context: Context, entries: List<AppLogEntry>) {
        val file = runtimeLogFile(context)
        file.parentFile?.mkdirs()
        FileOutputStream(file, true).bufferedWriter(StandardCharsets.UTF_8).use { writer ->
            entries.forEach { entry ->
                writer.append(entry.toLine())
                writer.newLine()
            }
        }
        if (file.length() > MAX_PERSISTED_BYTES) {
            trimPersistentFile(file)
        }
    }

    private fun rewritePersistentFile(context: Context, entries: List<AppLogEntry>) {
        val file = runtimeLogFile(context)
        if (entries.isEmpty()) {
            file.delete()
            return
        }
        file.parentFile?.mkdirs()
        file.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
            entries.forEach { entry ->
                writer.append(entry.toLine())
                writer.newLine()
            }
        }
        if (file.length() > MAX_PERSISTED_BYTES) {
            trimPersistentFile(file)
        }
    }

    private fun trimPersistentFile(file: File) {
        val lines = file.readLines(StandardCharsets.UTF_8).takeLast(MAX_ENTRIES)
        file.writeText(lines.joinToString(separator = "\n"), StandardCharsets.UTF_8)
        file.appendText("\n", StandardCharsets.UTF_8)
    }

    private fun runtimeLogFile(context: Context): File {
        return File(File(context.filesDir, LOG_DIR_NAME), RUNTIME_LOG_FILE)
    }

    private fun loadPersistentEntries(context: Context): List<AppLogEntry> {
        return synchronized(persistenceFileLock) {
            val file = runtimeLogFile(context)
            if (!file.exists()) return@synchronized emptyList()
            runCatching {
                file.readLines(StandardCharsets.UTF_8)
                    .takeLast(MAX_ENTRIES)
                    .mapIndexedNotNull { index, line -> parseLine(line, index + 1L) }
            }.getOrDefault(emptyList())
        }
    }

    private fun parseLine(line: String, fallbackId: Long): AppLogEntry? {
        val match = logLineRegex.matchEntire(line.trim()) ?: return null
        val timestamp = runCatching {
            LocalDateTime
                .parse(match.groupValues[1], timeFormatter)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrDefault(System.currentTimeMillis())
        val level = runCatching { AppLogLevel.valueOf(match.groupValues[2]) }
            .getOrDefault(AppLogLevel.INFO)
        return AppLogEntry(
            id = fallbackId,
            timestampMs = timestamp,
            level = level,
            tag = match.groupValues[3],
            message = match.groupValues[4]
        )
    }

    private val logLineRegex =
        Regex("""^\[(.+)] \[(DEBUG|INFO|WARN|ERROR)] \[(.+)] (.*)$""")

    private const val LEGACY_HOOK_TAG_PREFIX = "Hook/"
}
