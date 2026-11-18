package com.mi.onextbox.lsp

import com.mi.onextbox.ui.common.ShellLogger

/** Root command serialization is independent of the feature-definition facade. */
internal object LspMirrorCommands {
    @Synchronized fun execute(tag: String, commands: List<String>): Boolean {
        if (commands.isEmpty()) return true
        val script = "(set -e;\n" + commands.joinToString("\n") + "\n)"
        val success = runCatching {
            ShellLogger.exec(tag, script).isSuccess ||
                ShellLogger.exec("$tag su", "su -c ${quote(script)}").isSuccess
        }.getOrDefault(false)
        LspPreferenceStore.invalidateMirrors()
        if (!success) ConfigSyncStatus.failed(tag)
        return success
    }

    fun quote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"
}
