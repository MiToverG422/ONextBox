package com.mi.onextbox.lsp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface
import java.lang.ref.WeakReference
import java.lang.reflect.Proxy
import java.util.WeakHashMap
import kotlin.math.roundToInt

/** Renders host preferences inside the host COUI expandable component, not RecyclerView item animations. */
internal class SettingsNativeAppInfoPanel(
    private val context: Context,
    private val loader: ClassLoader,
    private val header: Any,
    private val rows: List<Any>,
    private val canToggle: () -> Boolean,
    private val onStateChanged: (Boolean, Boolean) -> Unit,
) {
    val view = ModernReflect.newInstance(type("com.coui.appcompat.cardlist.COUICardListSelectedItemLayout"), context) as LinearLayout
    private val list = ModernReflect.newInstance(type("com.coui.appcompat.expandable.COUIExpandableRecyclerView"), context) as ViewGroup
    private val holders = WeakHashMap<Any, Any>()
    private val rowPadding = WeakHashMap<View, IntArray>()
    private var cardTopInset = 0
    private var cardBottomInset = 0
    private val observers = mutableListOf<Any>()
    private val main = Handler(Looper.getMainLooper())
    private var expanded = false
    private var disposed = false
    private var refreshPending = false
    private var dividerFadeAvailable = true
    private var headerView: WeakReference<View>? = null
    private val titleDividerPaint = Paint()
    private val decoration = ModernReflect.newInstance(type("androidx.recyclerview.widget.COUIRecyclerView\$COUIDividerItemDecoration"), context)
    private var attachListener: View.OnAttachStateChangeListener? = null

    init {
        // The outer native card retains four corners throughout the height animation.
        // Inner native rows remain rectangular; no corner flags are switched on click.
        view.orientation = LinearLayout.VERTICAL
        call(view, "setPositionInGroup", 4)
        cardTopInset = view.paddingTop
        cardBottomInset = view.paddingBottom
        view.setPaddingRelative(view.paddingStart, 0, view.paddingEnd, 0)
        call(view, "consumeDispatchingEventForState", false)
        view.isClickable = false
        call(view, "setBackgroundAnimationEnabled", false)
        list.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        list.isNestedScrollingEnabled = false
        list.overScrollMode = View.OVER_SCROLL_NEVER
        list.isVerticalScrollBarEnabled = false
        call(list, "setEnablePointerDownAction", false)
        call(list, "setLayoutManager", ModernReflect.newInstance(type("androidx.recyclerview.widget.COUILinearLayoutManager"), context))
        dividerInstances[decoration] = WeakReference(this)
        call(list, "addItemDecoration", decoration)
        call(list, "setAdapter", adapter())
        call(list, "setOnGroupClickListener", proxy(type("com.coui.appcompat.expandable.COUIExpandableRecyclerView\$OnGroupClickListener")) { name, _ ->
            if (name != "onGroupClick") null else if (disposed || !canToggle()) true else {
                changeState(!expanded, true)
                // The official component owns expansion, reversal, clipping, height, and opacity.
                false
            }
        })
        view.addView(list)
        attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                // Settings finalizes its page theme after creating the preference models.
                // Resolve the card surface from the attached page, not the early fragment theme.
                val pageContext = (view.parent as? View)?.context ?: view.context
                val colorAttribute = pageContext.resources.getIdentifier("couiColorCardBackground", "attr", pageContext.packageName)
                require(colorAttribute != 0) { "Native card surface attribute missing" }
                val colors = pageContext.obtainStyledAttributes(intArrayOf(colorAttribute))
                val color = try { colors.getColor(0, 0) } finally { colors.recycle() }
                call(view, "refreshCardBg", color)
            }
            override fun onViewDetachedFromWindow(v: View) {
                call(view, "resetBackgroundAnimation")
                finishAnimations()
            }
        }.also(view::addOnAttachStateChangeListener)
    }

    fun setExpanded(value: Boolean, animate: Boolean) {
        if (disposed || expanded == value) return
        changeState(value, animate)
        if (!animate && !value) {
            finishAnimations()
            call(connector(), "collapseGroup", 0)
        } else {
            call(list, if (value) "expandGroup" else "collapseGroup", 0)
        }
    }

    fun refreshRow(preference: Any) {
        if (disposed) return
        // Rebinding during the official dummy-view animation would invalidate its cached holders.
        if (call(connector(), "isAllAnimatorEnd") != true) {
            if (!refreshPending) {
                refreshPending = true
                main.postDelayed({ refreshPending = false; refreshAll() }, 50L)
            }
            return
        }
        holders.entries.toList().filter { it.value === preference }.forEach { bind(it.key, preference) }
    }

    fun dispose() {
        disposed = true
        main.removeCallbacksAndMessages(null)
        call(view, "resetBackgroundAnimation")
        finishAnimations()
        attachListener?.let(view::removeOnAttachStateChangeListener)
        attachListener = null
        var failure: Throwable? = null
        listOf<() -> Unit>(
            { call(list, "setOnGroupClickListener", null) },
            { detachAdapter() },
            { call(list, "removeItemDecoration", decoration) },
        ).forEach { cleanup -> runCatching(cleanup).onFailure { if (failure == null) failure = it else failure?.addSuppressed(it) } }
        dividerInstances.remove(decoration)
        holders.clear()
        rowPadding.clear()
        headerView = null
        observers.clear()
        failure?.let { throw it }
    }

    private fun detachAdapter() {
        val oldConnector = ModernReflect.getObjectField(list, "mConnector")
        // Both COUI overloads reject a null adapter: the expandable overload constructs
        // a connector and immediately dereferences it; the RecyclerView overload throws.
        // A nonvirtual parent call performs real RecyclerView recycling and observer
        // cleanup while the old expandable adapter is still available to its callbacks.
        val setter = ModernReflect.findMethodExact(type("androidx.recyclerview.widget.RecyclerView"),
            "setAdapter", arrayOf(type("androidx.recyclerview.widget.RecyclerView\$Adapter")))
        ModernHookRuntime.requireModule().getInvoker(setter)
            .setType(XposedInterface.Invoker.Type.ORIGIN)
            .invokeSpecial(list, *arrayOfNulls<Any>(1))
        if (oldConnector != null) {
            val oldAdapter = ModernReflect.getObjectField(oldConnector, "mExpandableListAdapter")
            if (oldAdapter != null) {
                val observer = ModernReflect.getObjectField(oldConnector, "mDataSetObserver")
                call(oldAdapter, "unregisterAdapterDataObserver", observer)
            }
            // Do not call setExpandableListAdapter(null): it also dereferences null.
            ModernReflect.setObjectField(oldConnector, "mExpandableListAdapter", null)
        }
        ModernReflect.setObjectField(list, "mAdapter", null)
        ModernReflect.setObjectField(list, "mConnector", null)
        view.removeView(list)
    }

    private fun refreshAll() {
        if (!disposed) rows.forEach(::refreshRow)
    }

    private fun changeState(value: Boolean, animate: Boolean) {
        if (expanded == value) return
        expanded = value
        // Leave the header's native release animation running during expansion.
        onStateChanged(value, animate)
    }

    private fun adapter(): Any = proxy(type("com.coui.appcompat.expandable.COUIExpandableRecyclerAdapter")) { name, args ->
        when (name) {
            "getGroupCount", "getGroupTypeCount" -> 1
            "getChildrenCount" -> rows.size
            "getGroupType", "getChildType" -> 0
            "getGroupId", "getCombinedGroupId" -> 0L
            "getChildId" -> (args!![1] as Int).toLong() + 1L
            "getCombinedChildId" -> Long.MIN_VALUE or (args!![1] as Long)
            "getGroup" -> header
            "getChild" -> rows[args!![1] as Int]
            "areAllItemsEnabled", "hasStableIds", "isChildSelectable" -> true
            "isEmpty" -> false
            "onCreateGroupView" -> createHolder(args!![0] as ViewGroup, header, true)
            "onCreateChildView" -> createHolder(args!![0] as ViewGroup, rows.first(), false)
            "onBindGroupView" -> { bind(args!![2]!!, header); null }
            "onBindChildView" -> { bind(args!![3]!!, rows[args[1] as Int]); null }
            "onGroupExpanded" -> { changeState(true, true); null }
            "onGroupCollapsed" -> { changeState(false, true); null }
            "registerAdapterDataObserver" -> { observers.add(args!![0]!!); null }
            "unregisterAdapterDataObserver" -> { observers.remove(args!![0]); null }
            "onViewRecycled" -> {
                val holder = args!![0]!!
                val root = ModernReflect.getObjectField(holder, "itemView") as View
                call(root, "resetBackgroundAnimation")
                rowPadding.remove(root)
                holders.remove(holder)
                null
            }
            "setHasStableIds", "onRestoreView" -> null
            else -> error("Unsupported native expandable callback: $name")
        }
    }

    private fun createHolder(parent: ViewGroup, preference: Any, isHeader: Boolean): Any {
        val layout = call(preference, "getLayoutResource") as Int
        val root = LayoutInflater.from(context).inflate(layout, parent, false)
        if (isHeader) {
            call(root, "setPositionInGroup", 2)
            root.setPaddingRelative(root.paddingStart, root.paddingTop + cardTopInset,
                root.paddingEnd, root.paddingBottom + cardBottomInset)
            root.measure(View.MeasureSpec.makeMeasureSpec(context.resources.displayMetrics.widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            // The native expansion requires the group to end before the list's lower edge.
            list.minimumHeight = root.measuredHeight + 1
        }
        return ModernReflect.newInstance(type("androidx.preference.PreferenceViewHolder"), root)
    }

    private fun bind(holder: Any, preference: Any) {
        call(holder, "resetState")
        call(preference, "onBindViewHolder", holder)
        val root = ModernReflect.getObjectField(holder, "itemView") as View
        call(root, "setPositionInGroup", 2)
        rowPadding[root] = intArrayOf(root.paddingStart, root.paddingTop, root.paddingEnd, root.paddingBottom)
        applyRowInsets(root, preference)
        // Keep the shared card surface while each native row owns its press mask.
        root.background = null
        if (holders[holder] !== preference) call(root, "resetBackgroundAnimation")
        call(root, "consumeDispatchingEventForState", false)
        call(root, "setBackgroundAnimationEnabled", true)
        if (preference === header) {
            headerView = WeakReference(root)
        }
        holders[holder] = preference
    }

    private fun applyRowInsets(root: View, preference: Any) {
        val padding = rowPadding[root] ?: return
        val top = if (preference === header) cardTopInset else 0
        val bottom = if (preference === header || preference === rows.lastOrNull()) cardBottomInset else 0
        root.setPaddingRelative(padding[0], padding[1] + top, padding[2], padding[3] + bottom)
    }

    private fun connector(): Any = requireNotNull(ModernReflect.getObjectField(list, "mConnector"))

    private fun titleDividerOpacity(): Float {
        val group = requireNotNull(call(connector(), "getGroupInfo", 0))
        return SettingsAppInfoRules.titleDividerOpacity(
            animating = ModernReflect.getObjectField(group, "animating") as Boolean,
            expanded = expanded,
            expanding = ModernReflect.getObjectField(group, "expanding") as Boolean,
            height = ModernReflect.getObjectField(group, "dummyHeight") as Int,
            totalHeight = ModernReflect.getObjectField(group, "totalHeight") as Int,
        )
    }

    private fun drawDividers(canvas: Canvas, draw: () -> Any?): Any? {
        if (!dividerFadeAvailable) return draw()
        val frame = runCatching {
            val opacity = titleDividerOpacity()
            val root = headerView?.get()
            if (opacity >= 1f || root == null || root.parent !== list) null else {
                val top = (root.y + root.height).toInt().toFloat()
                val stroke = maxOf(1, call(decoration, "getDividerStrokeWidth") as Int)
                val rtl = root.layoutDirection == View.LAYOUT_DIRECTION_RTL
                val left = (root.x + if (rtl) root.paddingEnd else root.paddingStart).toInt().toFloat()
                val right = (root.x + root.width - if (rtl) root.paddingStart else root.paddingEnd).toInt().toFloat()
                titleDividerPaint.set(call(decoration, "getPaint") as Paint)
                val pressPosition = ModernReflect.getObjectField(decoration, "mPressDividerPos") as Int
                val alphaField = if (pressPosition == 0 || pressPosition == 1) "mPressDividerAlpha" else "mOriginAlpha"
                titleDividerPaint.alpha = ((ModernReflect.getObjectField(decoration, alphaField) as Int) * opacity).roundToInt()
                DividerFrame(top, top + stroke, left, right, list.width.toFloat())
            }
        }.getOrElse {
            dividerFadeAvailable = false
            HookLog.w("ONextBox-AppInfo", "Title divider fade unavailable; native drawing retained", it)
            null
        } ?: return draw()
        // Draw the body separators unchanged, excluding only the header separator's pixel band.
        // Never change the shared native paint: the dummy body uses it during the same animation.
        val bodySave = canvas.save()
        val result: Any?
        try {
            canvas.clipOutRect(0f, frame.top, frame.width, frame.bottom)
            result = draw()
        } finally {
            canvas.restoreToCount(bodySave)
        }
        // Only the header uses a separate copy of COUI's paint. Native body rendering runs
        // exactly once, with its original color, alpha, press feedback and animation untouched.
        canvas.drawRect(frame.left, frame.top, frame.right, frame.bottom, titleDividerPaint)
        return result
    }

    private data class DividerFrame(val top: Float, val bottom: Float, val left: Float, val right: Float, val width: Float)

    private fun finishAnimations() {
        runCatching {
            val animations = ModernReflect.getObjectField(connector(), "animatorSparseArray") as SparseArray<*>
            for (index in 0 until animations.size()) {
                val animation = animations.valueAt(index) as android.animation.ValueAnimator
                // Finish with the official completion callbacks, leaving no dummy row after detach.
                animation.end()
            }
        }.onFailure { HookLog.w("ONextBox-AppInfo", "Native panel animation cleanup failed", it) }
    }

    private fun type(name: String) = ModernReflect.findClass(name, loader)
    private fun call(owner: Any, name: String, vararg args: Any?) = ModernReflect.callMethod(owner, name, *args)

    companion object {
        private val dividerInstances = WeakHashMap<Any, WeakReference<SettingsNativeAppInfoPanel>>()

        fun hookDividers(loader: ClassLoader, prefix: String) {
            val divider = ModernReflect.findClass("androidx.recyclerview.widget.COUIRecyclerView\$COUIDividerItemDecoration", loader)
            divider.declaredMethods.filter { it.name == "getDividerInsetStart" || it.name == "getDividerInsetEnd" }.forEach { method ->
                ModernHookRegistry.installFast(prefix + method.toGenericString(), method, XposedInterface.Hooker { chain ->
                    if (dividerInstances[chain.thisObject]?.get() == null) chain.proceed() else {
                        val root = if (method.parameterCount == 1) {
                            chain.getArg(0)?.let { ModernReflect.getObjectField(it, "itemView") as? View }
                        } else {
                            (chain.getArg(0) as? ViewGroup)?.getChildAt(chain.getArg(1) as Int)
                        }
                        if (root == null) chain.proceed() else if (method.name == "getDividerInsetStart") root.paddingStart else root.paddingEnd
                    }
                })
            }
            val draw = divider.declaredMethods.single { it.name == "onDrawOver" && it.parameterCount == 3 }
            ModernHookRegistry.installFast(prefix + "header-fade", draw, XposedInterface.Hooker { chain ->
                val panel = dividerInstances[chain.thisObject]?.get()
                if (panel == null) chain.proceed() else panel.drawDividers(chain.getArg(0) as Canvas) { chain.proceed() }
            })
        }

        private fun proxy(type: Class<*>, action: (String, Array<out Any?>?) -> Any?): Any =
            Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { target, method, args ->
                when (method.name) {
                    "hashCode" -> System.identityHashCode(target)
                    "equals" -> target === args?.get(0)
                    "toString" -> "ONextBox native ${type.simpleName}"
                    else -> action(method.name, args)
                }
            }
    }
}
