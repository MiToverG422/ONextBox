package com.mi.onextbox.notifications

import kotlinx.serialization.Serializable

@Serializable
enum class NotificationPermissionKind { Notifications, Banner, LockScreen, Silent, LiveActivity }

@Serializable
data class NotificationAppTarget(val packageName: String, val uid: Int)

@Serializable
enum class NotificationBatchIssue { Protected, Unavailable, IdentityChanged, StateChanged, WriteFailed }

@Serializable
data class NotificationAppState(
    val target: NotificationAppTarget,
    val values: Map<NotificationPermissionKind, Int> = emptyMap(),
    val issue: NotificationBatchIssue? = null,
)

@Serializable
data class NotificationMutation(
    val target: NotificationAppTarget,
    val kind: NotificationPermissionKind,
    val before: Int,
    val after: Int,
)

@Serializable
data class NotificationMutationResult(
    val mutation: NotificationMutation,
    val changed: Boolean = false,
    val issue: NotificationBatchIssue? = null,
)

@Serializable
data class NotificationBatchReport(val results: List<NotificationMutationResult>) {
    val changes: List<NotificationMutation> get() = results.filter { it.changed }.map { it.mutation }
    val failed: Int get() = results.count { it.issue != null }
    val unchanged: Int get() = results.count { it.issue == null && !it.changed }
}

interface NotificationBatchBackend {
    fun guard(target: NotificationAppTarget): NotificationBatchIssue?
    fun read(target: NotificationAppTarget, kind: NotificationPermissionKind): Int
    fun write(target: NotificationAppTarget, kind: NotificationPermissionKind, value: Int)
}

class NotificationBatchEngine(private val backend: NotificationBatchBackend, private val userId: Int) {
    fun inspect(target: NotificationAppTarget): NotificationAppState {
        val issue = guard(target)
        if (issue != null) return NotificationAppState(target, issue = issue)
        val values = buildMap {
            NotificationPermissionKind.entries.forEach { kind ->
                runCatching { backend.read(target, kind) }.getOrNull()?.let { put(kind, it) }
            }
        }
        return NotificationAppState(target, values,
            if (values.isNotEmpty()) null else NotificationBatchIssue.Unavailable)
    }

    fun apply(mutations: List<NotificationMutation>): NotificationBatchReport {
        require(mutations.size <= 1500)
        require(mutations.distinctBy { it.target to it.kind }.size == mutations.size)
        return NotificationBatchReport(mutations.map { mutation ->
            val issue = guard(mutation.target)
            if (issue != null) return@map NotificationMutationResult(mutation, issue = issue)
            val current = runCatching { backend.read(mutation.target, mutation.kind) }.getOrNull()
                ?: return@map NotificationMutationResult(mutation, issue = NotificationBatchIssue.Unavailable)
            when {
                current == mutation.after -> NotificationMutationResult(mutation)
                current != mutation.before -> NotificationMutationResult(mutation, issue = NotificationBatchIssue.StateChanged)
                !validValue(mutation.kind, mutation.after) -> NotificationMutationResult(mutation, issue = NotificationBatchIssue.Unavailable)
                else -> {
                    runCatching { backend.write(mutation.target, mutation.kind, mutation.after) }
                    val actual = runCatching { backend.read(mutation.target, mutation.kind) }.getOrNull()
                    NotificationMutationResult(mutation, changed = actual == mutation.after,
                        issue = if (actual == mutation.after) null else NotificationBatchIssue.WriteFailed)
                }
            }
        })
    }

    private fun guard(target: NotificationAppTarget): NotificationBatchIssue? {
        if (userId < 0 || target.uid < 0 || target.uid / 100000 != userId ||
            !target.packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))) {
            return NotificationBatchIssue.IdentityChanged
        }
        if (target.uid % 100000 < 10000) return NotificationBatchIssue.Protected
        return runCatching { backend.guard(target) }.getOrDefault(NotificationBatchIssue.Unavailable)
    }

    companion object {
        fun validValue(kind: NotificationPermissionKind, value: Int): Boolean = when (kind) {
            NotificationPermissionKind.LockScreen -> value in setOf(-1000, -1, 0, 1)
            NotificationPermissionKind.Silent -> value in -1..1
            else -> value == 0 || value == 1
        }

        fun plan(states: List<NotificationAppState>, selected: Set<String>, kinds: Set<NotificationPermissionKind>, enabled: Boolean): List<NotificationMutation> =
            states.filter { it.target.packageName in selected && it.issue == null }.flatMap { state ->
                kinds.mapNotNull { kind -> state.values[kind]?.let { before ->
                    val after = when {
                        kind == NotificationPermissionKind.Silent -> if (enabled) -1 else 1
                        enabled && kind == NotificationPermissionKind.LockScreen && before == 0 -> 0
                        enabled -> 1
                        kind == NotificationPermissionKind.LockScreen -> -1
                        else -> 0
                    }
                    NotificationMutation(state.target, kind, before, after)
                } }
            }

        fun undo(changes: List<NotificationMutation>): List<NotificationMutation> = changes.map { it.copy(before = it.after, after = it.before) }
    }
}
