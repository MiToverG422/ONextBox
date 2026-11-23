package com.mi.onextbox.lsp

import java.util.UUID

internal object DisplayDensitySettings {
    const val KEY = "system_display_density"
    const val DEFAULT = -1
    private const val RESULT = "onextbox_display_density"

    fun resultKey(user: Int) = "${RESULT}_$user"
    fun requestId(): String = UUID.randomUUID().toString()

    enum class Action { Save, Adjust, Restore }

    data class Request(
        val id: String,
        val user: Int,
        val boot: Int,
        val original: Int,
        val target: Int,
        val keep: Boolean,
        val action: Action,
    ) {
        fun encode() = listOf(id, user, boot, original, target, if (keep) 1 else 0, action.name)
            .joinToString("|")
    }

    data class Result(val id: String, val boot: Int, val active: Boolean, val success: Boolean) {
        fun encode() = listOf(id, boot, if (active) 1 else 0, if (success) 1 else 0).joinToString("|")
    }

    data class Reconciliation(val write: Int?, val retain: Boolean)

    fun request(value: String?): Request? {
        val fields = value?.split('|', limit = 8) ?: return null
        if (fields.size != 7 || fields[0].length != 36) return null
        return runCatching {
            val (id, user, boot, original, target) = fields
            UUID.fromString(id)
            Request(id, user.toInt(), boot.toInt(), original.toInt(), target.toInt(),
                fields[5] == "1", Action.valueOf(fields[6])).also {
                require(it.user >= 0 && it.boot >= 0)
                require(validOverride(it.original) && validOverride(it.target))
                require(fields[5] == "0" || fields[5] == "1")
            }
        }.getOrNull()
    }

    fun result(value: String?): Result? {
        val fields = value?.split('|', limit = 5) ?: return null
        if (fields.size != 4) return null
        val boot = fields[1].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        return Result(fields[0], boot, fields[2] == "1", fields[3] == "1")
    }

    fun decide(request: Request, boot: Int, previous: Result?, currentOverride: Int?): Reconciliation? {
        require(request.boot <= boot)
        if (previous?.id == request.id) {
            if (previous.boot == boot) return null
            if (previous.success && !previous.active) return Reconciliation(null, false)
        }
        val onBoot = request.boot != boot
        return when (request.action) {
            Action.Restore -> if (onBoot) Reconciliation(null, false)
                else reconcile(request.original, request.target, currentOverride, onBoot = false, keep = false)
            Action.Save, Action.Adjust -> if (onBoot) {
                reconcile(request.original, request.target, currentOverride, onBoot = true, keep = request.keep)
            } else {
                Reconciliation(request.target.takeIf {
                    request.action == Action.Adjust && it != (currentOverride ?: DEFAULT)
                }, true)
            }
        }
    }

    fun reconcile(
        original: Int, target: Int, currentOverride: Int?, onBoot: Boolean, keep: Boolean,
    ): Reconciliation {
        val current = currentOverride ?: DEFAULT
        val resetOnBoot = onBoot && original != target && (current == DEFAULT || current == original)
        if (current != target && !resetOnBoot) return Reconciliation(null, false)
        val retain = onBoot && keep
        val desired = if (retain) target else original
        return Reconciliation(desired.takeIf { it != current }, retain)
    }

    fun validOverride(dpi: Int) = dpi == DEFAULT || dpi >= 72

    fun overrideFor(dpi: Int, physical: Int) = if (dpi == physical) DEFAULT else dpi
}
