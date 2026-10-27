package com.mi.onextbox.lsp

import android.content.Context
import android.graphics.Canvas
import android.view.View
import com.mi.onextbox.lsp.compat.ModernReflect
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.AccessibleObject
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal class LauncherGridTargets(val values: Array<*>) {
    val constructor = values[0] as Constructor<*>
    val options = values[1] as Method
    val currentGrid = values[2] as Method
    val refresh = values[3] as Method
    val itemConstructor = values[4] as Constructor<*>
    val columns = values[5] as Field
    val textRows = values[6] as Field
    val wordlessRows = values[7] as Field
    val selected = values[8] as Field
    val visible = values[9] as Field
    val all = values[10] as Field
    val index = values[11] as Field
    val wordless = values[12] as Method
    val recycler = values[13] as Method
    val simple = values[14] as Method
    val mode = values[15] as Method
    val largeStyle = values[16] as Field
    val draw = values[17] as Method
    val preview = (values[18] as Array<*>).filterIsInstance<Field>()
    val busy = (values[19] as Array<*>).filterIsInstance<Method>()
    val callers = (values[20] as Array<*>).filterIsInstance<Executable>()
    val supported = values[21] as Method

    init {
        (values.filterIsInstance<AccessibleObject>() + preview + busy + callers).forEach { it.isAccessible = true }
    }

    fun allowed() = !largeStyle.getBoolean(null) && simple.invoke(mode.invoke(null)) != true
    fun wordless() = wordless.invoke(null) == true
    fun grid(item: Any, withoutWords: Boolean) =
        LauncherGrid(columns.getInt(item), (if (withoutWords) wordlessRows else textRows).getInt(item))

    fun option(item: Any) = LauncherGridOption(columns.getInt(item), textRows.getInt(item), wordlessRows.getInt(item))

    fun item(option: LauncherGridOption): Any = itemConstructor.newInstance().also {
        columns.setInt(it, option.columns)
        textRows.setInt(it, option.textRows)
        wordlessRows.setInt(it, option.wordlessRows)
        selected.setBoolean(it, false)
    }

    companion object {
        const val SCHEMA_SIZE = 22
        private const val ADAPTER = "com.android.launcher.togglebar.adapter.ToggleBarDialogLayoutAdapter"
        private const val ITEM = "com.android.launcher.togglebar.item.ToggleBarLayoutConfigItem"

        fun resolve(loader: ClassLoader, apkPath: String): LauncherGridTargets {
            if (SystemAdaptationDetector.current() == SystemAdaptationVersion.ColorOs16) return resolveColorOs16(loader)
            System.loadLibrary("dexkit")
            return DexKitBridge.create(apkPath).use { dex ->
                dex.setThreadNum(1)
                val adapter = ModernReflect.findClass(ADAPTER, loader)
                val item = ModernReflect.findClass(ITEM, loader)
                val init = dex.findMethod {
                    matcher { declaredClass = ADAPTER; usingStrings("selectionStateInfo") }
                }.single { it.isConstructor }
                val constructor = init.getConstructorInstance(loader)
                require(constructor.parameterCount == 2 && Context::class.java.isAssignableFrom(constructor.parameterTypes[0]))
                val read = dex.findMethod {
                    matcher { usingStrings("layout_cellcount_x", "layout_cellcount_y"); paramTypes(Context::class.java.name); returnType = "int[]" }
                }.single()
                val options = init.invokes.single {
                    it.className == read.className && it.returnTypeName == "java.util.ArrayList" && it.paramCount == 0
                }
                val current = init.invokes.single {
                    it.className == read.className && it.returnTypeName == "int[]" && it.paramTypeNames == listOf(Context::class.java.name)
                }
                val refresh = dex.findMethod {
                    matcher {
                        declaredClass = ADAPTER; usingStrings("updateLayoutConfigs : inoperable update, mAllLayoutConfigs.isEmpty()")
                        paramTypes("boolean"); returnType = "void"
                    }
                }.single()
                val row = dex.findMethod {
                    matcher { declaredClass = ITEM; paramTypes(); returnType = "int" }
                }.single { candidate -> candidate.invokes.any { it.returnTypeName == "boolean" && it.paramCount == 0 } }
                val wordlessData = row.invokes.single { it.returnTypeName == "boolean" && it.paramCount == 0 }
                val wordless = wordlessData.getMethodInstance(loader)
                require(Modifier.isStatic(wordless.modifiers))
                val supported = options.callers.single {
                    it.className == read.className && it.returnTypeName == "boolean" &&
                        it.paramTypeNames == listOf("int", "int") &&
                        it.invokes.any { invoked -> invoked.descriptor == wordlessData.descriptor }
                }
                require(Modifier.isStatic(supported.getMethodInstance(loader).modifiers))
                val rowFields = row.usingFields.map { it.field }.distinctBy { it.descriptor }
                    .filter { it.typeName == "int" && !Modifier.isStatic(it.modifiers) }
                    .map { it.getFieldInstance(loader).apply { isAccessible = true } }
                require(rowFields.size == 2)
                val itemConstructor = item.getDeclaredConstructor().apply { isAccessible = true }
                val probe = itemConstructor.newInstance()
                rowFields[0].setInt(probe, 701)
                rowFields[1].setInt(probe, 702)
                val selectedRow = row.getMethodInstance(loader).invoke(probe)
                require(selectedRow == 701 || selectedRow == 702)
                val active = rowFields[if (selectedRow == 701) 0 else 1]
                val inactive = rowFields.single { it != active }
                val withoutWords = wordless.invoke(null) == true
                val textRows = if (withoutWords) inactive else active
                val wordlessRows = if (withoutWords) active else inactive
                val columns = item.declaredFields.single { it.type == Int::class.javaPrimitiveType && !Modifier.isStatic(it.modifiers) && it !in rowFields }
                val selected = item.declaredFields.single { it.type == Boolean::class.javaPrimitiveType && !Modifier.isStatic(it.modifiers) }
                val count = dex.findMethod { matcher { declaredClass = ADAPTER; name = "getItemCount"; paramTypes(); returnType = "int" } }.single()
                val visible = count.usingFields.map { it.field }.distinctBy { it.descriptor }
                    .single { it.typeName == "java.util.ArrayList" }.getFieldInstance(loader)
                val all = adapter.declaredFields.single { it.type == ArrayList::class.java && it != visible && !Modifier.isStatic(it.modifiers) }
                val index = adapter.declaredFields.single { it.type == Int::class.javaPrimitiveType && !Modifier.isStatic(it.modifiers) }
                val largeStyle = adapter.declaredFields.single { it.type == Boolean::class.javaPrimitiveType && Modifier.isStatic(it.modifiers) }
                val recycler = adapter.getMethod("getMRecyclerView")
                require(View::class.java.isAssignableFrom(recycler.returnType))
                val modeType = ModernReflect.findClass("com.android.launcher.mode.LauncherModeManager", loader)
                val mode = modeType.getDeclaredMethod("getInstance")
                val simple = modeType.getDeclaredMethod("isInSimpleMode")
                val previewType = ModernReflect.findClass("com.android.launcher.togglebar.views.ToggleBarLayoutItemPreview", loader)
                val drawData = dex.findMethod {
                    matcher {
                        declaredClass = previewType.name; name = "onDraw"
                        paramTypes(Canvas::class.java.name); returnType = "void"
                    }
                }.single()
                val draw = drawData.getMethodInstance(loader)
                val preview = LauncherGridPreviewFields.resolve(previewType,
                    drawData.usingFields.map { it.field.getFieldInstance(loader) })
                val helper = ModernReflect.findClass("com.android.launcher.togglebar.LayoutSettingsHelper", loader)
                val busy = listOf("getSIsSwitchingCurrentCellLayout", "isInApplySwitchLayout", "isCurrentCellLayoutInAdjustment")
                    .map { name -> helper.getDeclaredMethod(name).also { require(it.returnType == Boolean::class.javaPrimitiveType && Modifier.isStatic(it.modifiers)) } }
                val callers = (refresh.callers + options.callers + supported.callers).distinctBy { it.descriptor }.map {
                    if (it.isConstructor) it.getConstructorInstance(loader) else it.getMethodInstance(loader)
                }
                LauncherGridTargets(arrayOf(constructor, options.getMethodInstance(loader), current.getMethodInstance(loader),
                    refresh.getMethodInstance(loader), itemConstructor, columns, textRows, wordlessRows, selected, visible,
                    all, index, wordless, recycler, simple, mode, largeStyle, draw, preview,
                    busy.toTypedArray(), (callers + constructor).distinct().toTypedArray(), supported.getMethodInstance(loader)))
            }
        }

        private fun resolveColorOs16(loader: ClassLoader): LauncherGridTargets {
            val adapter = ModernReflect.findClass(ADAPTER, loader)
            val item = ModernReflect.findClass(ITEM, loader)
            val launcher = ModernReflect.findClass("com.android.launcher.Launcher", loader)
            val selection = ModernReflect.findClass("com.android.launcher.togglebar.item.TemporarySelectionStateInfo", loader)
            val config = ModernReflect.findClass("com.android.launcher.UiConfig", loader)
            val wordless = ModernReflect.findClass("com.android.launcher.togglebar.WordlessDesktopHelper", loader)
                .getDeclaredMethod("isOpened")
            val options = config.getDeclaredMethod("getSupportLayout")
            require(List::class.java.isAssignableFrom(options.returnType) && Modifier.isStatic(options.modifiers))
            val supported = config.getDeclaredMethod("isSupportLayout", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            val modeType = ModernReflect.findClass("com.android.launcher.mode.LauncherModeManager", loader)
            val preview = ModernReflect.findClass("com.android.launcher.togglebar.views.ToggleBarLayoutItemPreview", loader)
            // C16 onDraw delegates to drawPreviewCell; hook its field-owning implementation.
            val draw = preview.getDeclaredMethod("drawPreviewCell", Canvas::class.java)
            val helper = ModernReflect.findClass("com.android.launcher.togglebar.LayoutSettingsHelper", loader)
            val busy = listOf("getSIsSwitchingCurrentCellLayout", "isInApplySwitchLayout", "isCurrentCellLayoutInAdjustment")
                .map { helper.getDeclaredMethod(it).also { method ->
                    require(method.returnType == Boolean::class.javaPrimitiveType && Modifier.isStatic(method.modifiers))
                } }
            val constructor = adapter.getDeclaredConstructor(launcher, selection)
            val callers = (adapter.declaredMethods.toList() + config.declaredMethods + helper.declaredMethods + preview.declaredMethods)
                .filter { !Modifier.isAbstract(it.modifiers) && !Modifier.isNative(it.modifiers) }
            return LauncherGridTargets(arrayOf(
                constructor, options,
                config.getDeclaredMethod("getCurrentLayoutSettings", Context::class.java),
                adapter.getDeclaredMethod("updateLayoutConfigs", Boolean::class.javaPrimitiveType), item.getDeclaredConstructor(),
                item.getDeclaredField("mColumn"), item.getDeclaredField("mRow"), item.getDeclaredField("mRowForWordlessDesktop"),
                item.getDeclaredField("mSelected"), adapter.getDeclaredField("mLayoutConfigs"), adapter.getDeclaredField("mAllLayoutConfigs"),
                adapter.getDeclaredField("mSelectedPos"), wordless, adapter.getMethod("getMRecyclerView"),
                modeType.getDeclaredMethod("isInSimpleMode"), modeType.getDeclaredMethod("getInstance"),
                adapter.getDeclaredField("isWordlessOnlyDevice"), draw,
                LauncherGridPreviewFields.resolve(preview, preview.declaredFields.toList(), colorOs16 = true),
                busy.toTypedArray(), (callers + constructor).toTypedArray(), supported,
            ))
        }
    }
}
