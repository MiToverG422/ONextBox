package com.mi.onextbox.lsp

import android.app.Activity
import android.view.ViewGroup
import com.mi.onextbox.lsp.compat.ModernReflect
import java.lang.reflect.AccessibleObject
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal class LauncherGridApplyTargets(val values: Array<*>) {
    private val tracker = values[0] as Field
    private val created = values[1] as Method
    val apply = values[2] as Method
    private val loading = values[3] as Method
    private val workspace = values[4] as Method
    private val dragController = values[5] as Method
    private val dragging = values[6] as Method
    private val stateManager = values[7] as Method
    private val transitioning = values[8] as Method
    private val state = values[9] as Method
    private val normal = values[10] as Field
    private val background = values[11] as Field
    val taskConstructor = values[12] as Constructor<*>
    val completed = values[13] as Method
    private val setGrid = values[14] as Method
    private val taskStatus = values[15] as Method

    init { values.filterIsInstance<AccessibleObject>().forEach { it.isAccessible = true } }

    fun activity(): Activity? = (created.invoke(tracker.get(null)) as? Activity)?.takeIf {
        apply.parameterTypes[0].isInstance(it) && !it.isDestroyed && !it.isFinishing
    }

    fun ready(activity: Activity): Boolean {
        if (loading.invoke(activity) == true) return false
        val workspace = workspace.invoke(activity) as? ViewGroup ?: return false
        if (workspace.childCount == 0) return false
        if (dragging.invoke(dragController.invoke(activity)) == true) return false
        val manager = stateManager.invoke(activity) ?: return false
        if (transitioning.invoke(manager) == true) return false
        val current = state.invoke(manager)
        return current === normal.get(null) || current === background.get(null)
    }

    fun started(task: Any?) = task != null && (taskStatus.invoke(task) as? Enum<*>)?.name in setOf("RUNNING", "FINISHED")

    fun restorePreview(activity: Activity, grid: LauncherGrid) {
        val group = workspace.invoke(activity) as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            val cell = group.getChildAt(index)
            if (setGrid.declaringClass.isInstance(cell)) setGrid.invoke(cell, grid.columns, grid.rows, false)
        }
    }

    companion object {
        const val SCHEMA_SIZE = 16

        fun completionMethod(task: Class<*>): Method =
            task.getDeclaredMethod("onPostExecute", Any::class.java).also {
                require(it.returnType == Void.TYPE && !Modifier.isStatic(it.modifiers) &&
                    !Modifier.isAbstract(it.modifiers))
            }

        fun resolve(loader: ClassLoader): LauncherGridApplyTargets {
            val launcher = ModernReflect.findClass("com.android.launcher.Launcher", loader)
            val base = ModernReflect.findClass("com.android.launcher3.Launcher", loader)
            val tracker = base.getDeclaredField("ACTIVITY_TRACKER")
            require(Modifier.isStatic(tracker.modifiers))
            val created = tracker.type.getMethod("getCreatedActivity")
            require(Activity::class.java.isAssignableFrom(created.returnType))
            val helper = ModernReflect.findClass("com.android.launcher.togglebar.LayoutSettingsHelper", loader)
            val signature = arrayOf(launcher, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!,
                Boolean::class.javaPrimitiveType!!)
            val apply = helper.getDeclaredMethod("applyLayoutSettings", *signature)
            require(Modifier.isStatic(apply.modifiers) && apply.returnType == Void.TYPE)
            val task = helper.declaredClasses.flatMap { it.declaredConstructors.toList() }
                .single { it.parameterTypes.contentEquals(signature) }
            val async = Class.forName("android.os.AsyncTask", false, loader)
            require(async.isAssignableFrom(task.declaringClass))
            val completed = completionMethod(task.declaringClass)
            val workspace = launcher.getMethod("getWorkspace")
            require(ViewGroup::class.java.isAssignableFrom(workspace.returnType))
            val drag = launcher.getMethod("getDragController")
            val manager = launcher.getMethod("getStateManager")
            val loading = launcher.getMethod("isWorkspaceLoading")
            val dragging = drag.returnType.getMethod("isDragging")
            val transitioning = manager.returnType.getMethod("isInTransition")
            require(listOf(loading, dragging, transitioning).all { it.returnType == Boolean::class.javaPrimitiveType })
            val state = ModernReflect.findClass("com.android.launcher3.LauncherState", loader)
            val cell = ModernReflect.findClass("com.android.launcher3.OplusCellLayout", loader)
            return LauncherGridApplyTargets(arrayOf(tracker, created, apply, loading,
                workspace, drag, dragging, manager,
                transitioning, manager.returnType.getMethod("getState"),
                state.getField("NORMAL"), state.getField("BACKGROUND_APP"), task, completed,
                cell.getMethod("setGridSize", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType), async.getMethod("getStatus")))
        }
    }
}
