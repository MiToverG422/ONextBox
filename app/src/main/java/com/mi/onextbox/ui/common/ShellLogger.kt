package com.mi.onextbox.ui.common

import com.topjohnwu.superuser.Shell

internal object ShellLogger {
    fun exec(tag: String, vararg commands: String): Shell.Result {
        commands.forEach { command ->
            AppLogStore.d("Shell", "[$tag] $ ${command.take(240)}")
        }
        val result = Shell.cmd(*commands).exec()
        AppLogStore.i(
            "Shell",
            "[$tag] success=${result.isSuccess}, out=${result.out.size}, err=${result.err.size}"
        )
        result.out.take(3).forEach { line ->
            AppLogStore.d("ShellOut", "[$tag] ${line.take(240)}")
        }
        result.err.take(5).forEach { line ->
            AppLogStore.w("ShellErr", "[$tag] ${line.take(240)}")
        }
        return result
    }
}
