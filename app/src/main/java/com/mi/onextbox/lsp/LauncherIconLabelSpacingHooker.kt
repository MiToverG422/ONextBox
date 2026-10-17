package com.mi.onextbox.lsp

import android.view.View
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface

internal object LauncherIconLabelSpacingHooker {
    private const val TAG = "ONextBox-LauncherIconLabelSpacing"
    private const val BUBBLE_TEXT_VIEW = "com.android.launcher3.BubbleTextView"
    private const val MULTIPLE_SIZE_GROUP_VIEW = "com.android.launcher3.stack.view.IMultipleSizeGroupView"
    private const val CARD_NAME_HELPER = "com.android.launcher3.card.utils.CardNameHelper"

    // Workspace(0), all-apps drawer(1), folder(2) and the drawer's category folder(11) share the
    // same icon/label layout path. Hot-seat(8) and super power workspace(9) are excluded.
    private val targetDisplays = setOf(0, 1, 2, 11)

    // Native workspace icon-label padding in px, captured from a workspace icon (display=0) getter
    // call. Avoids reading the obfuscated DeviceProfile field (j4.j.k) which changes across OTAs.
    @Volatile
    private var workspaceBasePaddingPx = -1

    fun hook(loader: ClassLoader) {
        runCatching { installIcons(loader) }.onSuccess {
            HookLog.i(TAG, "Icon spacing gate installed")
        }.onFailure {
            HookLog.w(TAG, "Icon spacing unavailable, stock spacing retained", it)
        }

        runCatching { installFolderName(loader) }.onSuccess {
            HookLog.i(TAG, "Folder label spacing gate installed")
        }.onFailure {
            HookLog.w(TAG, "Folder label spacing unavailable, stock spacing retained", it)
        }

        runCatching { installCardName(loader) }.onSuccess {
            HookLog.i(TAG, "Card label spacing gate installed")
        }.onFailure {
            HookLog.w(TAG, "Card label spacing unavailable, stock spacing retained", it)
        }
    }

    // Normal and large app icons are BubbleTextViews whose icon/label gap is the compound drawable
    // padding. Hook the getter updateIconBounds() reads on every setIcon().
    private fun installIcons(loader: ClassLoader) {
        val bubble = ModernReflect.findClass(BUBBLE_TEXT_VIEW, loader)
        val getter = bubble.declaredMethods.single {
            it.name == "getRegularCompoundDrawablePadding"
                    && it.parameterTypes.isEmpty()
                    && it.returnType == Int::class.javaPrimitiveType
        }
        check(ModernHookRuntime.requireModule().deoptimize(getter)) {
            "Unable to deoptimize icon-label padding getter"
        }

        ModernHookRegistry.installFast("$TAG:padding", getter, XposedInterface.Hooker { chain ->
            val original = chain.proceed() as? Int ?: 0
            val offset = LauncherGestureSettings.iconLabelSpacingOffsetXposed()
            if (offset == 0 || !LauncherGestureSettings.iconLabelSpacingEnabledXposed()) return@Hooker original
            val host = chain.thisObject as? View ?: return@Hooker original
            val display = runCatching {
                val extInstance =
                    ModernReflect.getObjectField(host, "mOPlusBtvExtV2") ?: return@runCatching -1
                ModernReflect.callMethod(extInstance, "getIconDisplay") as? Int ?: -1
            }.getOrDefault(-1)
            if (display !in targetDisplays) return@Hooker original
            val delta = (offset * host.resources.displayMetrics.density + 0.5f).toInt()
            // Folder icons (display=2) use a larger native base gap (folderDisplayOption.
            // iconDrawablePaddingDp = 6dp) than workspace icons (1dp). Normalize the folder base to
            // the workspace value so the spacing is uniform everywhere.
            val base = if (display == 2) {
                if (workspaceBasePaddingPx >= 0) workspaceBasePaddingPx else original
            } else {
                // Capture the native workspace base on any non-folder target display.
                if (display == 0) workspaceBasePaddingPx = original
                original
            }
            (base + delta).coerceAtLeast(0)
        })
    }

    // A big folder's label is a bottom-aligned BubbleTextView whose compound drawable padding is
    // hard-coded to 0; its icon/label gap is the label's top padding (folder_icon_title_padding_top).
    // The real layout lives in IMultipleSizeGroupView.placeGroupName(Launcher, DeviceProfile, String);
    // FlexibleFolderIcon only forwards to it, so hook the interface default method directly.
    private fun installFolderName(loader: ClassLoader) {
        val groupView = ModernReflect.findClass(MULTIPLE_SIZE_GROUP_VIEW, loader)
        val placeGroupName = groupView.declaredMethods.single {
            it.name == "placeGroupName"
                    && it.parameterTypes.size == 3
                    && it.returnType == Boolean::class.javaPrimitiveType
        }
        runCatching { ModernHookRuntime.requireModule().deoptimize(placeGroupName) }
        ModernHookRegistry.installCompat(
            "$TAG:folder-name", placeGroupName, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val offset = LauncherGestureSettings.iconLabelSpacingOffsetXposed()
                    if (offset == 0 || !LauncherGestureSettings.iconLabelSpacingEnabledXposed()) return
                    val host = param.thisObject as? View ?: return
                    val label = runCatching { ModernReflect.callMethod(host, "getTitle") as? View }
                        .getOrNull() ?: return
                    shiftLabelDown(label, offset)
                }
            })
    }

    // A service card's name label uses the same top-padding-based gap, set in CardNameHelper's
    // measureCardName()/measureGroupCardName(). Shift it identically.
    private fun installCardName(loader: ClassLoader) {
        val helper = ModernReflect.findClass(CARD_NAME_HELPER, loader)
        listOf("measureCardName", "measureGroupCardName").forEach { name ->
            val measure =
                helper.declaredMethods.single { it.name == name && it.parameterTypes.size == 1 && !it.isBridge }
            check(ModernHookRuntime.requireModule().deoptimize(measure)) {
                "Unable to deoptimize card name measurement"
            }
            ModernHookRegistry.installCompat(
                "$TAG:card-name:$name", measure, object : ModernMethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val offset = LauncherGestureSettings.iconLabelSpacingOffsetXposed()
                        if (offset == 0 || !LauncherGestureSettings.iconLabelSpacingEnabledXposed()) return
                        val host = param.thisObject ?: return
                        val label = runCatching {
                            ModernReflect.callMethod(host, "getCardName") as? View
                        }.getOrNull() ?: return
                        shiftLabelDown(label, offset)
                    }
                })
        }
    }

    private fun shiftLabelDown(label: View, offset: Int) {
        val delta = (offset * label.resources.displayMetrics.density + 0.5f).toInt()
        if (delta == 0) return
        // The label is bottom-aligned with a fixed height, so its text top is
        // containerBottom - height + paddingTop. Increasing only paddingTop moves the text down by
        // delta; changing height too would cancel the shift.
        label.setPadding(
            label.paddingLeft,
            (label.paddingTop + delta).coerceAtLeast(0),
            label.paddingRight,
            label.paddingBottom,
        )
        label.requestLayout()
    }
}
