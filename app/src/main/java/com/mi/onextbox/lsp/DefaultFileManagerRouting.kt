package com.mi.onextbox.lsp

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo

internal object DefaultFileManagerRouting {
    private val stockPackages = setOf("com.coloros.filemanager", "com.android.documentsui", "com.google.android.documentsui")
    private val pickerActions = setOf(Intent.ACTION_GET_CONTENT, Intent.ACTION_OPEN_DOCUMENT,
        Intent.ACTION_CREATE_DOCUMENT, Intent.ACTION_OPEN_DOCUMENT_TREE)
    private val browseActions = setOf(
        "oplus.intent.action.filemanager.FILE_APK_VIEW",
        "oppo.intent.action.FILE_COMPRESS_VIEW",
        "oplus.intent.action.filemanager.FILE_DOCUMENT_VIEW",
        "oplus.intent.action.filemanager.FILE_MEDIA_VIEW",
        "oplus.intent.action.filemanager.BROWSER_FILE",
    )
    private val folderTypes = setOf("resource/folder", "vnd.android.document/directory")

    fun preferredPicker(intent: Intent, query: List<*>, selected: String): ResolveInfo? {
        if (selected.isBlank() || intent.action !in pickerActions || !mayRoute(intent)) return null
        return query.filterIsInstance<ResolveInfo>().firstOrNull {
            it.activityInfo?.let { info -> info.packageName == selected && available(info) } == true
        }
    }

    fun mayRoute(intent: Intent): Boolean {
        if (intent.selector != null) return false
        if (intent.action == Intent.ACTION_CHOOSER) return chooserTarget(intent)?.let(::mayRoute) == true
        val target = intent.component?.packageName ?: intent.`package`
        if (target != null && target !in stockPackages) return false
        return intent.action in pickerActions || intent.action in browseActions ||
            ((intent.action == Intent.ACTION_MAIN || intent.action == null) &&
                (target in stockPackages || intent.hasCategory(Intent.CATEGORY_APP_FILES))) ||
            (intent.action == Intent.ACTION_VIEW && intent.type in folderTypes)
    }

    fun redirect(pm: PackageManager, original: Intent, resolvedType: String?, selected: String, caller: String?): Intent? {
        if (selected.isBlank() || caller == selected || !mayRoute(original)) return null
        if (original.action == Intent.ACTION_CHOOSER) {
            val inner = chooserTarget(original) ?: return null
            val routed = redirect(pm, inner, inner.type, selected, caller) ?: return null
            // Keep the chooser's additional camera/other options and result callbacks when present.
            if (hasChooserOptions(original)) return Intent(original).putExtra(Intent.EXTRA_INTENT, routed)
            return routed.apply {
                addFlags(original.flags)
                if (clipData == null) clipData = original.clipData
            }
        }
        val target = original.component?.packageName ?: original.`package`
        if (target != null && target !in stockPackages) return null
        if (caller != null && caller == target) return null
        val probe = Intent(original).apply {
            component = null
            setPackage(selected)
            if (type == null && resolvedType != null) setDataAndType(data, resolvedType)
        }
        if (original.action in pickerActions) return matching(pm, probe)
        if (original.action == Intent.ACTION_VIEW) return matching(pm, probe)
        if (original.action in browseActions) return matching(pm, probe) ?: launch(pm, selected, original)
        if (original.action == Intent.ACTION_MAIN || original.action == null) {
            val appFiles = original.hasCategory(Intent.CATEGORY_APP_FILES)
            val stockLaunch = target in stockPackages && isLauncher(pm, original.component)
            if (appFiles || stockLaunch) return launch(pm, selected, original)
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun chooserTarget(intent: Intent): Intent? = runCatching {
        intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)?.takeIf {
            it.action in pickerActions && it.selector == null
        }
    }.getOrNull()

    private fun hasChooserOptions(intent: Intent): Boolean = listOf(
        Intent.EXTRA_INITIAL_INTENTS, Intent.EXTRA_ALTERNATE_INTENTS, Intent.EXTRA_CHOOSER_TARGETS,
        Intent.EXTRA_REPLACEMENT_EXTRAS, Intent.EXTRA_CHOSEN_COMPONENT_INTENT_SENDER,
        Intent.EXTRA_EXCLUDE_COMPONENTS,
    ).any(intent::hasExtra)

    @Suppress("DEPRECATION")
    private fun isLauncher(pm: PackageManager, component: ComponentName?): Boolean {
        if (component == null) return false
        return listOf(Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_INFO).any { category ->
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category).setPackage(component.packageName), 0)
                .any { result -> result.activityInfo?.let { info ->
                    available(info) && (info.name == component.className || info.targetActivity == component.className)
                } == true }
        }
    }

    @Suppress("DEPRECATION")
    private fun matching(pm: PackageManager, probe: Intent): Intent? {
        val match = pm.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
            .firstOrNull { it.activityInfo?.let(::available) == true }?.activityInfo ?: return null
        return probe.apply { component = ComponentName(match.packageName, match.name) }
    }

    @Suppress("DEPRECATION")
    private fun launch(pm: PackageManager, selected: String, original: Intent): Intent? {
        val launch = pm.getLaunchIntentForPackage(selected) ?: return null
        val component = launch.component ?: return null
        if (!available(pm.getActivityInfo(component, 0))) return null
        return Intent(original).apply {
            action = launch.action
            categories?.toList()?.forEach(::removeCategory)
            launch.categories?.forEach(::addCategory)
            setDataAndType(null, null)
            clipData = null
            flags = flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION).inv()
            setPackage(selected)
            this.component = component
        }
    }

    fun available(info: ActivityInfo): Boolean = info.enabled && info.exported &&
        info.applicationInfo?.enabled == true && info.permission.isNullOrEmpty()
}
