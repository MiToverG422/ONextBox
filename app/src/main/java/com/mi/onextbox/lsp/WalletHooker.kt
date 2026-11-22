package com.mi.onextbox.lsp

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import io.github.libxposed.api.XposedInterface
import java.util.concurrent.ConcurrentHashMap
import java.util.WeakHashMap

internal object WalletHooker {
    private const val swipe = "com.nearme.wallet.nfc.ui.NfcConsumeActivity"
    private val promotions = setOf("BANNER", "WEALTH", "LOAN", "WELFARE", "PROMOTION", "RECOMMEND",
        "MALL_SKU_ALBUM", "FOOD_REBATE", "NOVICE_EXCLUSIVE", "NOVICE_EXCLUSIVE_2", "INSTALLMENT_MALL",
        "ALIGN_PIC_ALBUM", "TOP_PICTURE", "PIC_ALBUM", "DOUBLE_BOARD", "THREE_BOARD", "FOUR_BOARD",
        "THREE_CARD_NO_BORDER", "LEFT_PIC_RIGHT_DOUBLE", "PIC_CARD", "GRAPHICS_BOARD", "SCROLL_VIEW")

    fun hook(loader: ClassLoader) {
        fun bind(owner: String, name: String, result: Class<*>, vararg args: Class<*>, callback: XposedInterface.Hooker) {
            runCatching {
                val method = Class.forName(owner, false, loader).getDeclaredMethod(name, *args)
                check(method.returnType == result)
                ModernHookRegistry.installFast("wallet:$method", method, callback)
            }.onFailure { android.util.Log.w("ONextBox-Wallet", "Unavailable: $owner.$name (${it.javaClass.simpleName})") }
        }
        val boolean = Boolean::class.javaPrimitiveType!!
        val void = Void.TYPE
        fun suppress(option: WalletOptions, value: Any?) = XposedInterface.Hooker { chain ->
            if (option.active()) value else chain.proceed()
        }
        val operations = "com.finshell.homepage.operate.HomePageActionController"
        bind(operations, "G", boolean, Context::class.java, String::class.java, callback = suppress(WalletOptions.MARKETING, false))
        bind(operations, "w0", void, Context::class.java, String::class.java, Long::class.javaPrimitiveType!!, boolean,
            callback = suppress(WalletOptions.MARKETING, null))
        fun type(name: String) = runCatching { Class.forName(name, false, loader) }.getOrNull()
        val integer = Int::class.javaPrimitiveType!!
        val nfc = type("com.nearme.wallet.db.NfcCard")
        val circle = type("com.finshell.finui.widget.imageview.CircleNetworkImageView")
        val holder = type("com.nearme.pay.business.cardpackage.adapter.CardPackageListAdapter\$a")
        val model = type("com.nearme.bean.ModelData")
        if (holder != null && model != null && circle != null) {
            val imageField = holder.declaredFields.singleOrNull { it.type == circle }?.apply { isAccessible = true }
            val reader = WalletCardReader(model, false)
            if (imageField != null) bind(holder.name, "a0", void, holder, model, boolean, integer, String::class.java) { chain ->
                chain.proceed().also {
                    val image = imageField.get(chain.getArg(0)) as? ImageView
                    if (image != null) WalletCardArtwork.bind(image, reader.identify(image, chain.getArg(1)))
                }
            }
        }
        if (nfc != null) {
            val reader = WalletCardReader(nfc, true)
            fun applyArt(image: ImageView?, card: Any?) {
                if (image != null) WalletCardArtwork.bind(image, reader.identify(image, card))
            }
            val frame = type("com.nearme.pay.business.consume.cardframe.e")
            val face = runCatching { frame?.getDeclaredField("mCardImage")?.apply { isAccessible = true } }.getOrNull()
            if (frame != null && face != null) bind(frame.name, "onBindCommonData", void, nfc, integer) { chain ->
                chain.proceed().also { applyArt(face.get(chain.thisObject) as? ImageView, chain.getArg(0)) }
            }
            val quick = "com.nearme.pay.business.consume.card.quick.QuickPkgCardViewBehavior"
            bind(quick, "createCustomCardView", View::class.java, android.widget.FrameLayout::class.java, nfc) { chain ->
                chain.proceed().also { applyArt(it as? ImageView, chain.getArg(1)) }
            }
            bind(quick, "updateNfcCardViewData", boolean, View::class.java, nfc) { chain ->
                chain.proceed().also { if (it == true) applyArt(chain.getArg(0) as? ImageView, chain.getArg(1)) }
            }
            if (circle != null) bind("com.nearme.pay.dragonfly.widget.DragonflyConsumeCardsFrameController",
                "T", void, circle, nfc) { chain ->
                chain.proceed().also { applyArt(chain.getArg(0) as? ImageView, chain.getArg(1)) }
            }
        }
        for ((actionBar, getter, init) in listOf(
            Triple("com.finshell.doubleclick.view.dcpbusiness.DcpActionBarBusiness", "j", "m"),
            Triple("com.finshell.quickcardpkg.view.qcpuibusiness.QcpActionBarBusiness", "i", "k"))) {
            val toolbar = type(actionBar)?.let { runCatching { it.getDeclaredMethod(getter) }.getOrNull() } ?: continue
            val getMenu = runCatching { toolbar.returnType.getMethod("getMenu") }.getOrNull() ?: continue
            bind(actionBar, init, void) { chain ->
                chain.proceed().also {
                    val bar = toolbar.invoke(chain.thisObject) as? View
                    if (bar != null && WalletOptions.TOOLBAR.active()) bar.visibility = View.GONE
                    if (bar != null && WalletOptions.FEEDBACK.active()) {
                        val id = bar.resources.getIdentifier("menu_item_help", "id", WalletOptions.PACKAGE)
                        (getMenu.invoke(bar) as? Menu)?.findItem(id)?.isVisible = false
                    }
                }
            }
        }
        val questionnaire = "com.finshell.questionnaire.QuestionnaireViewModel"
        type("androidx.appcompat.app.AppCompatActivity")?.let {
            bind(questionnaire, "B", void, it, String::class.java, String::class.java,
                callback = suppress(WalletOptions.SURVEY, null))
        }
        type("iz.a")?.let {
            bind(questionnaire, "X", void, Context::class.java, it, callback = suppress(WalletOptions.SURVEY, null))
        }
        type("com.nearme.domain.rsp.LaunchScreenRspVo")?.let {
            bind("com.finshell.homepage.splash.SplashCacheController", "filterOut", it, List::class.java,
                boolean, Map::class.java, boolean, callback = suppress(WalletOptions.SPLASH, null))
        }
        val channel = type("com.nearme.wallet.main.domain.rsp.ChannelRspVo")
        val showType = channel?.let { runCatching { it.getMethod("getShowType") }.getOrNull() }
        if (channel != null && showType != null) {
            bind("com.finshell.homepage.adapter.viewholder.HeadAdViewHolder", "x0", void, boolean, channel) { chain ->
                if (WalletOptions.HOME.active()) chain.proceed(arrayOf(chain.getArg(0), null)) else chain.proceed()
            }
            val index = "com.finshell.homepage.adapter.IndexDataAdapter"
            fun filter(rows: List<*>) = rows.filterNot { row -> channel.isInstance(row) && showType.invoke(row) in promotions }
            bind(index, "F0", void, List::class.java, boolean, channel, android.widget.RelativeLayout::class.java) { chain ->
                val rows = chain.getArg(0) as? List<*>
                if (rows == null || !WalletOptions.HOME.active()) chain.proceed()
                else chain.proceed(arrayOf(filter(rows), chain.getArg(1), chain.getArg(2), chain.getArg(3)))
            }
            // The cache refresh can replace the displayed list without calling F0.
            bind(index, "b0", void, List::class.java) { chain ->
                val rows = chain.getArg(0) as? List<*>
                if (rows == null || !WalletOptions.HOME.active()) chain.proceed()
                else chain.proceed(arrayOf(filter(rows)))
            }
        }
        // Discover ad row types from the holder factories each adapter registers, including vendor variants.
        val base = "com.chad.library.adapter.base.temp.BaseMultiItemAdapter"
        val adapter = type(base)
        val factory = type(base + "\$c")
        val adapters = setOf("com.finshell.quickcardpkg.view.list.DcpMainIndexAdapter",
            "com.finshell.quickcardpkg.view.list.QuickCardPkgAdapter")
        val adTypes = WeakHashMap<Any, MutableSet<Int>>()
        if (adapter != null && factory != null) {
            bind(base, "q0", adapter, Int::class.javaPrimitiveType!!, factory) { chain ->
                val host = chain.thisObject
                if (host != null && host.javaClass.name in adapters) {
                    val provider = chain.getArg(1)
                    val ad = provider?.javaClass?.declaredMethods?.any {
                        val holder = it.returnType.name
                        holder.startsWith("com.finshell.advertisement.") && holder.endsWith("ViewHolder") ||
                            holder.startsWith("com.finshell.quickcardpkg.view.list.viewhold.QcpShortVideo")
                    } == true
                    if (ad) synchronized(adTypes) { adTypes.getOrPut(host) { mutableSetOf() }.add(chain.getArg(0) as Int) }
                }
                chain.proceed()
            }
            val rowType = type("if.t")?.let { runCatching { it.getMethod("f") }.getOrNull() }
            if (rowType != null) for (owner in adapters) bind(owner, "setData", void, List::class.java) { chain ->
                if (!WalletOptions.FEED.active()) return@bind chain.proceed()
                val ids = synchronized(adTypes) { adTypes[chain.thisObject]?.toSet() } ?: return@bind chain.proceed()
                val rows = chain.getArg(0) as? List<*> ?: return@bind chain.proceed()
                chain.proceed(arrayOf(rows.filterNot { row -> rowType.declaringClass.isInstance(row) && rowType.invoke(row) in ids }))
            }
        }
        bind(swipe, "onCreateOptionsMenu", boolean, Menu::class.java) { chain ->
            chain.proceed().also {
                if (WalletOptions.FEEDBACK.active()) {
                    val activity = chain.thisObject as Activity
                    val id = activity.resources.getIdentifier("consume_help", "id", WalletOptions.PACKAGE)
                    (chain.getArg(0) as Menu).findItem(id)?.isVisible = false
                }
            }
        }
        bind(swipe, "onResume", void) { chain ->
            val activity = chain.thisObject as Activity
            if (WalletOptions.NFC.active()) WalletNfcSession.enter(activity)
            chain.proceed()
        }
        bind(swipe, "onStop", void) { chain ->
            chain.proceed().also { WalletNfcSession.leave(chain.thisObject as Activity) }
        }
        bind(LayoutInflater::class.java.name, "inflate", View::class.java, Int::class.javaPrimitiveType!!,
            ViewGroup::class.java, boolean) { chain ->
            val resources = (chain.thisObject as LayoutInflater).context.resources
            val layout = layoutName(resources, chain.getArg(0) as Int)
            if (layout.isEmpty()) return@bind chain.proceed()
            val parent = chain.getArg(1) as? ViewGroup
            val before = parent?.childCount ?: 0
            val result = chain.proceed()
            if (parent != null && result === parent) {
                for (index in before until parent.childCount) applyLayout(parent.getChildAt(index), layout)
            } else if (result is View) applyLayout(result, layout)
            result
        }
        android.util.Log.i("ONextBox-Wallet", "Wallet presentation hooks ready")
    }

    private val feed = setOf("qcp_item_ad_ranking", "quick_card_item_ad_operation_view", "qcp_layou_app_activation_ad_horizontal",
        "qcp_layou_app_activation_ad_vertical", "qcp_layou_syn_big_text_down_ad", "qcp_layou_syn_big_text_up_ad",
        "qcp_layou_syn_small_ad", "qcp_layout_short_video_big_card", "qcp_layout_short_video_big_mul_card",
        "qcp_layout_short_video_small_card")
    private val known = feed + setOf("layout_sun_shine", "layout_operate_sun_shine_item",
        "layout_operate_sun_shine_consume", "activity_nfc_open_success", "layout_pay_attach_result", "fragment_dcp_main_page", "fragment_quick_card_pkg", "layout_nfc_consume", "activity_consume_complete",
        "layout_consume_recommend_business_view", "consume_appbar_with_divider_layout", "qcp_layout_anim_float_ad")
    private val layoutNames = ConcurrentHashMap<Int, String>()

    private fun layoutName(res: Resources, id: Int): String = layoutNames.getOrPut(id) {
        runCatching { res.getResourceEntryName(id) }.getOrNull()?.takeIf(known::contains).orEmpty()
    }

    /** Campaign views can be made visible again after network data or a recycled holder is bound. */
    private class CampaignVisibility(val view: View, val option: WalletOptions) :
        View.OnAttachStateChangeListener, android.view.ViewTreeObserver.OnGlobalLayoutListener {
        private var previous: Int? = null
        init {
            view.addOnAttachStateChangeListener(this)
            if (view.isAttachedToWindow) onViewAttachedToWindow(view)
        }
        override fun onViewAttachedToWindow(v: View) {
            v.viewTreeObserver.addOnGlobalLayoutListener(this)
            onGlobalLayout()
        }
        override fun onViewDetachedFromWindow(v: View) {
            if (v.viewTreeObserver.isAlive) v.viewTreeObserver.removeOnGlobalLayoutListener(this)
        }
        override fun onGlobalLayout() {
            if (option.active()) {
                if (view.visibility != View.GONE) {
                    previous = view.visibility
                    view.visibility = View.GONE
                }
            } else previous?.let {
                previous = null
                if (view.visibility == View.GONE) view.visibility = it
            }
        }
    }

    private fun applyLayout(root: View, layout: String) {
        fun child(id: String): View? = root.findViewById(root.resources.getIdentifier(id, "id", WalletOptions.PACKAGE))
        fun hide(child: View?, option: WalletOptions) {
            if (child != null) CampaignVisibility(child, option)
        }
        when (layout) {
            "layout_sun_shine", "layout_operate_sun_shine_item" -> hide(root, WalletOptions.HOME)
            "layout_operate_sun_shine_consume", "layout_consume_recommend_business_view" -> hide(root, WalletOptions.SWIPE_ADS)
            "activity_nfc_open_success", "layout_pay_attach_result" -> {
                hide(child("layout_operate_banner"), WalletOptions.SWIPE_ADS)
                hide(child("layout_operate_sunshine"), WalletOptions.SWIPE_ADS)
            }
            "fragment_dcp_main_page", "fragment_quick_card_pkg" -> {
                hide(child("appBarLayout"), WalletOptions.TOOLBAR)
                hide(child("app_bar_bg"), WalletOptions.TOOLBAR)
                hide(child("app_bar_center_clickable_view"), WalletOptions.TOOLBAR)
            }
            "consume_appbar_with_divider_layout" -> hide(root, WalletOptions.TOOLBAR)
            "qcp_layout_anim_float_ad" -> hide(root, WalletOptions.MARKETING)
            "activity_consume_complete" -> hide(child("banner_image"), WalletOptions.SWIPE_ADS)
            "layout_nfc_consume" -> hide(child("layout_operate"), WalletOptions.SHORTCUTS)
            in feed -> hide(root, WalletOptions.FEED)
        }
    }
}
