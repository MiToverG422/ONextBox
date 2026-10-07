package com.mi.onextbox.lsp

import android.util.Log
import io.github.libxposed.service.HookedTarget
import io.github.libxposed.service.XposedService

data class LspLoadedTarget(
    val processName: String,
    val uid: Int,
    val current: Boolean,
    val loadedVersionCode: Long,
)

data class LspFrameworkSnapshot(
    val apiVersion: Int,
    val frameworkVersionText: String?,
    val scopes: Set<String>?,
    val runningTargets: List<LspLoadedTarget>?,
)

internal fun formatLspFrameworkVersion(
    name: String,
    version: String,
    versionCode: Long?,
    api: Int,
): String {
    val title = listOf(name.trim().ifBlank { "LSPosed" }, version.trim())
        .filter(String::isNotBlank).joinToString(" ")
    val build = versionCode?.takeIf { it > 0 }?.let { " ($it)" }.orEmpty()
    return "$title$build / API $api"
}

/** Live framework queries, shared with preference synchronization. */
object LspFrameworkService {
    private const val TAG = "ONextBox-XposedService"
    private val serviceLock = Any()

    @Volatile
    private var service: XposedService? = null

    fun bind(bound: XposedService) {
        synchronized(serviceLock) { service = bound }
        LsposedScopeRequester.onFrameworkServiceChanged()
    }

    fun unbind(bound: XposedService): Boolean {
        val removed = synchronized(serviceLock) {
            if (service !== bound) false else {
                service = null
                true
            }
        }
        if (removed) LsposedScopeRequester.onFrameworkServiceChanged()
        return removed
    }

    fun isCurrent(bound: XposedService): Boolean = service === bound

    internal fun publishIfCurrent(bound: XposedService, publish: () -> Unit): Boolean =
        synchronized(serviceLock) {
            if (service !== bound) false else {
                publish()
                true
            }
        }

    fun readSnapshot(): LspFrameworkSnapshot? {
        val bound = service ?: return null
        val api = try {
            bound.apiVersion
        } catch (error: RuntimeException) {
            Log.w(TAG, "Framework query failed: ${error.javaClass.simpleName}")
            unbind(bound)
            return null
        }
        val version = runCatching {
            formatLspFrameworkVersion(
                bound.frameworkName,
                bound.frameworkVersion,
                runCatching { bound.frameworkVersionCode.toLong() }.getOrNull(),
                api,
            )
        }.getOrNull()
        val scopes = runCatching { bound.scope.toSet() }.onFailure {
            Log.w(TAG, "Scope query failed: ${it.javaClass.simpleName}")
        }.getOrNull()
        val targets = if (api >= XposedService.API_102) {
            runCatching {
                bound.runningTargets.map { target ->
                    LspLoadedTarget(
                        processName = target.processName,
                        uid = target.uid,
                        current = target.state == HookedTarget.State.UP_TO_DATE,
                        loadedVersionCode = target.loadedVersionCode,
                    )
                }
            }.onFailure {
                Log.w(TAG, "Loaded-target query failed: ${it.javaClass.simpleName}")
            }.getOrNull()
        } else null
        if (!isCurrent(bound)) return null
        return LspFrameworkSnapshot(api, version, scopes, targets)
    }
}
