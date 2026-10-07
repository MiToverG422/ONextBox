package com.mi.onextbox.lsp

import android.app.Activity
import com.mi.onextbox.lsp.compat.ModernHookBridge as XposedBridge
import com.mi.onextbox.lsp.compat.ModernMethodHook as XC_MethodHook
import com.mi.onextbox.lsp.compat.ModernReflect as XposedHelpers
import java.util.ArrayList
import java.util.HashMap
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** ColorOS App Market behavior and recommendation-page cleanup. */
internal object AppMarketHooker {
    private const val TAG = "ONextBox-AppMarket"
    private const val REGION_CHECKER_CLASS = "com.heytap.mspsdk.regionv3.impl.g"
    private const val REGION_CHECK_METHOD = "\u0560"
    private const val REGION_RESULT_CLASS = "com.heytap.mspsdk.regionv3.c"
    private const val COUNTRY_SWITCH_WORKER_CLASS =
        "com.heytap.mspsdk.regionv3.switcher.ui.fragment.SelectCountryFragment\$f"
    private const val COUNTRY_SWITCH_RESULT_METHOD = "\u037f"
    private const val SWITCH_TIPS_ACTIVITY_CLASS =
        "com.heytap.mspsdk.regionv3.switcher.ui.activity.SwitchDialogTipsActivity"
    private const val SWITCH_RUNTIME_CLASS = "com.heytap.mspsdk.regionv3.switcher.a"
    private const val SWITCH_UI_MANAGER_CLASS =
        "com.heytap.mspsdk.regionv3.switcher.ui.manager.a"
    private const val EXTRA_TARGET_REGION = "targetRegion"
    private const val SPLASH_TRANSACTION_CLASS = "a.a.a.w4f"
    private const val SPLASH_DTO_V4_CLASS =
        "com.heytap.cdo.splash.domain.dto.v4.SplashDtoV4"
    private const val CARD_DATA_PROCESSOR_CLASS = "a.a.a.sl2"
    private const val CARD_ADAPTER_PRESENTER_CLASS =
        "com.heytap.cdo.client.cards.page.base.adapter.CardAdapterPresenter"
    private const val CARD_API_ADAPTER_CLASS =
        "com.heytap.card.api.listener.CardApiRecycleViewAdapter"
    private const val SEARCH_ACTIVITY_CLASS =
        "com.heytap.market.search.core.activity.SearchActivity"
    private const val SEARCH_HOME_ADAPTER_PRESENTER_CLASS =
        "com.heytap.market.search.core.fragment.home.SearchHomeCardAdapterPresenter"
    private val SEARCH_RESULT_RESPONSE_CLASSES = listOf(
        "com.heytap.market.search.core.fragment.result.child.SearchResultRefreshControlLoader",
        "com.heytap.market.search.core.fragment.result.child.SearchResultDefaultChildPagingLoader",
        "com.heytap.market.search.core.fragment.result.group.b\$a",
    )
    private const val PAGING_RESPONSE_CLASS = "com.nearme.platform.loader.paging.e"
    private const val DETAIL_CONTENT_VIEW_CLASS =
        "com.heytap.cdo.client.detail.cn.app.tabcontent.detail.TabDetailContentView"
    private const val DETAIL_RECOMMEND_CONTENT_VIEW_CLASS =
        "com.heytap.cdo.client.detail.cn.app.tabcontent.recommend.TabRecommendContentView"
    private const val DETAIL_RECOMMEND_PAYLOAD_CLASS = "a.a.a.sn2"
    private const val DETAIL_REPO_CLASS =
        "com.heytap.cdo.client.detail.cn.app.tabcontent.detail.TabDetailRepo"
    private const val NETWORK_RESPONSE_CLASS = "com.nearme.platform.loader.network.d"
    private val DETAIL_CARD_ADAPTER_CLASSES = listOf(
        "com.heytap.card.api.listener.CardApiRecyclerViewAdapterProxy",
        "com.nearme.cards.adapter.RecyclerViewCardAdapter",
    )
    private const val MINE_FRAGMENT_CLASS = "com.heytap.market.mine.MineFragment"
    private const val VIEW_LAYER_WRAP_DTO_CLASS =
        "com.heytap.cdo.card.domain.dto.ViewLayerWrapDto"
    private val SEARCH_HOME_RECOMMENDATION_CODES = setOf(
        40118, // 人气搜索 / SearchHotRankCard
        40119, // 精品推荐 / HorizontalSmallIconAppCard
        100004, // 热门 App 合集 / SearchHotInstallRecycleCard
        100203, // 大家都在搜 / SearchAllLookingForCard
    )
    private val SEARCH_RESULT_RECOMMENDATION_CODES = SEARCH_HOME_RECOMMENDATION_CODES + setOf(
        531, // SearchRecommendCard
        40017, // Search commercialization text card
        40033, // Search five-image card
        40163, // GuessYouWantSearchCard
        100211, // SearchTrendingRankCard
    )
    private val SEARCH_RESULT_RECOMMENDATION_KEYS = setOf(56432, 56433)
    private val DETAIL_RECOMMENDATION_CODES = setOf(
        173, // ListAppsRelativeCard
        197, // CommentListRecommendApps
        40003, // DetailRecommend3App
        40004, // DetailRecommend5App
        40005, // DetailRecommend8App
        40074, // ListAppsRelativeCardBigIcon
    )
    private val DETAIL_RECOMMENDATION_KEYS = setOf(57963, 54067)
    private val SEARCH_RECOMMENDATION_TITLE_MARKERS = listOf(
        "人气热搜",
        "人气搜索",
        "人气精选",
        "精品推荐",
        "精品随心选",
        "潮流app尽在掌握",
        "本周热门",
        "热门app合集",
        "大家都在搜",
        "搜索结果页相关推荐",
    )
    private val DETAIL_RECOMMENDATION_TITLE_MARKERS = SEARCH_RECOMMENDATION_TITLE_MARKERS + listOf(
        "同类推荐",
        "相似应用",
        "相关推荐",
        "你可能还喜欢",
        "猜你喜欢",
    )

    private val hookedLoaders = ConcurrentHashMap.newKeySet<Int>()

    fun hook(classLoader: ClassLoader) {
        if (!hookedLoaders.add(System.identityHashCode(classLoader))) return

        installSplashRecommendationHook(classLoader)
        installCardRecommendationHook(classLoader)
        installSearchHomeRecommendationHook(classLoader)
        installSearchResultRecommendationHook(classLoader)
        installDetailRecommendationHook(classLoader)
        installMineRecommendationHook(classLoader)

        val checkerClass = XposedHelpers.findClassIfExists(REGION_CHECKER_CLASS, classLoader)
        val resultClass = XposedHelpers.findClassIfExists(REGION_RESULT_CLASS, classLoader)
        if (checkerClass == null || resultClass == null) {
            HookLog.w(TAG, "App Market region v3 classes were not found")
            return
        }

        runCatching {
            val handles = XposedBridge.hookAllMethods(
                checkerClass,
                REGION_CHECK_METHOD,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!LspConfig.isAppMarketRegionRestrictionBypassEnabledXposed()) return
                        val targetRegion = (param.args.firstOrNull() as? String)
                            ?.trim()
                            ?.uppercase(Locale.ROOT)
                            ?: return
                        if (!targetRegion.matches(Regex("[A-Z]{2}"))) return

                        val successResult = XposedHelpers.newInstance(resultClass)
                        XposedHelpers.callMethod(successResult, "\u052f", 0)
                        XposedHelpers.callMethod(successResult, "\u0588", "success")
                        param.result = successResult
                        HookLog.i(TAG, "Allowed App Market service-region switch to $targetRegion")
                    }
                },
            )
            check(handles.isNotEmpty()) { "Region checker method was not found" }
            HookLog.i(TAG, "App Market region switch checker hooked")
        }.onFailure { error ->
            HookLog.w(TAG, "Failed to hook App Market region switch checker", error)
        }

        // ColorOS 17's App Market can invoke the small static checker through an already compiled
        // direct-call path. In that case the method is registered as hooked but its interceptor is
        // not entered. The result is always handed to this UI worker before App Market decides
        // whether to continue its own switch transaction, so normalize only the rejected result
        // here and leave the original success callback/persistence flow intact.
        val switchWorkerClass = XposedHelpers.findClassIfExists(
            COUNTRY_SWITCH_WORKER_CLASS,
            classLoader,
        )
        if (switchWorkerClass == null) {
            HookLog.w(TAG, "App Market country switch worker was not found")
            return
        }
        runCatching {
            val handles = XposedBridge.hookAllMethods(
                switchWorkerClass,
                COUNTRY_SWITCH_RESULT_METHOD,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!LspConfig.isAppMarketRegionRestrictionBypassEnabledXposed()) return
                        val result = param.args.firstOrNull {
                            resultClass.isInstance(it)
                        } ?: return
                        val alreadyAllowed = runCatching {
                            XposedHelpers.callMethod(result, "\u052e") as? Boolean
                        }.getOrNull() == true
                        if (alreadyAllowed) return

                        XposedHelpers.callMethod(result, "\u052f", 0)
                        XposedHelpers.callMethod(result, "\u0588", "success")
                        HookLog.i(TAG, "Allowed App Market country switch at UI result boundary")
                    }
                },
            )
            check(handles.isNotEmpty()) { "Country switch result method was not found" }
            HookLog.i(TAG, "App Market country switch result boundary hooked")
        }.onFailure { error ->
            HookLog.w(TAG, "Failed to hook App Market country switch result boundary", error)
        }

        installRejectedSwitchActivityFallback(
            classLoader = classLoader,
            checkerClass = checkerClass,
            resultClass = resultClass,
        )
    }

    private fun installSplashRecommendationHook(classLoader: ClassLoader) {
        val transactionClass = XposedHelpers.findClassIfExists(
            SPLASH_TRANSACTION_CLASS,
            classLoader,
        ) ?: run {
            HookLog.w(TAG, "App Market splash transaction was not found")
            return
        }
        val splashDtoClass = XposedHelpers.findClassIfExists(SPLASH_DTO_V4_CLASS, classLoader)
            ?: run {
                HookLog.w(TAG, "App Market V4 splash DTO was not found")
                return
            }
        val target = transactionClass.declaredMethods.singleOrNull { method ->
            method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == Boolean::class.javaPrimitiveType &&
                method.returnType == splashDtoClass
        } ?: run {
            HookLog.w(TAG, "App Market splash data method was not found uniquely")
            return
        }

        runCatching {
            XposedBridge.hookMethod(
                target,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!LspConfig.isAppMarketRemoveSplashRecommendationsEnabledXposed()) {
                            return
                        }
                        param.result = null
                    }
                },
            )
            HookLog.i(TAG, "App Market C17 splash recommendation hook installed")
        }.onFailure { error ->
            HookLog.w(TAG, "Failed to hook App Market splash recommendations", error)
        }
    }

    private fun installCardRecommendationHook(classLoader: ClassLoader) {
        val processorClass = XposedHelpers.findClassIfExists(
            CARD_DATA_PROCESSOR_CLASS,
            classLoader,
        ) ?: run {
            HookLog.w(TAG, "App Market card data processor was not found")
            return
        }
        val target = processorClass.declaredMethods.singleOrNull { method ->
            method.name == "processData" &&
                method.parameterTypes.size == 4 &&
                List::class.java.isAssignableFrom(method.returnType)
        } ?: run {
            HookLog.w(TAG, "App Market card data processor method was not found uniquely")
            return
        }

        runCatching {
            XposedBridge.hookMethod(
                target,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val hideUpdateDownload = LspConfig
                            .isAppMarketRemoveUpdateDownloadRecommendationsEnabledXposed()
                        val hideSearchHome = LspConfig
                            .isAppMarketHideSearchHomeRecommendationsEnabledXposed()
                        if (!hideUpdateDownload && !hideSearchHome) return

                        if (hideUpdateDownload) {
                            val isUpdateOrDownloadPage =
                                Thread.currentThread().stackTrace.any { frame ->
                                    frame.className.startsWith(
                                        "com.heytap.market.appmanage.core.upgrade.",
                                    ) || frame.className.startsWith(
                                        "com.heytap.market.appmanage.core.download.",
                                    )
                                }
                            if (isUpdateOrDownloadPage) {
                                param.result = emptyList<Any>()
                                return
                            }
                        }

                        if (hideSearchHome) {
                            val cards = param.result as? List<*> ?: return
                            val filtered = cards.filterNot { card ->
                                val code = card?.let {
                                    runCatching {
                                        XposedHelpers.callMethod(it, "getCode") as? Number
                                    }.getOrNull()?.toInt()
                                }
                                code in SEARCH_HOME_RECOMMENDATION_CODES
                            }
                            if (filtered.size != cards.size) {
                                param.result = ArrayList(filtered)
                            }
                        }
                    }
                },
            )
            HookLog.i(TAG, "App Market C17 card recommendation hook installed")
        }.onFailure { error ->
            HookLog.w(TAG, "Failed to hook App Market card recommendations", error)
        }
    }

/** Search-home filtering at the final adapter, covering cached and network results. */
    private fun installSearchHomeRecommendationHook(classLoader: ClassLoader) {
        val presenterClass = XposedHelpers.findClassIfExists(
            SEARCH_HOME_ADAPTER_PRESENTER_CLASS,
            classLoader,
        ) ?: run {
            HookLog.w(TAG, "App Market search-home adapter presenter was not found")
            return
        }
        val target = presenterClass.declaredMethods.singleOrNull { method ->
            method.returnType == Void.TYPE &&
                method.parameterTypes.size == 2 &&
                List::class.java.isAssignableFrom(method.parameterTypes[1])
        } ?: run {
            HookLog.w(TAG, "App Market search-home submit method was not found uniquely")
            return
        }

        runCatching {
            XposedBridge.hookMethod(
                target,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!LspConfig.isAppMarketHideSearchHomeRecommendationsEnabledXposed()) {
                            return
                        }
                        val cards = param.args.getOrNull(1) as? List<*> ?: return
                        param.args[1] = filterCards(
                            cards = cards,
                            source = "search home",
                        ) { card ->
                            isSearchHomeRecommendation(card)
                        }
                    }
                },
            )
            HookLog.i(TAG, "App Market C17 search-home final card boundary hooked")
        }.onFailure { error ->
            HookLog.w(TAG, "Failed to hook App Market search-home recommendations", error)
        }
    }

    /** Search result pages use the base card presenter rather than the search-home subclass. */
    private fun installSearchResultRecommendationHook(classLoader: ClassLoader) {
        installSearchResultResponseHooks(classLoader)

        val presenterClass = XposedHelpers.findClassIfExists(
            CARD_ADAPTER_PRESENTER_CLASS,
            classLoader,
        ) ?: run {
            HookLog.w(TAG, "App Market base card adapter presenter was not found")
            return
        }

        runCatching {
            val handles = XposedBridge.hookAllMethods(
                presenterClass,
                "\u078e",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!LspConfig.isAppMarketHideSearchResultRecommendationsEnabledXposed()) {
                            return
                        }
                        val presenter = param.thisObject ?: return
                        if (presenter.javaClass.name == SEARCH_HOME_ADAPTER_PRESENTER_CLASS) {
                            return
                        }
                        val context = findCardAdapterContext(presenter) ?: return
                        if (context.javaClass.name != SEARCH_ACTIVITY_CLASS) return
                        val response = param.args.getOrNull(1) ?: return
                        val dto = runCatching {
                            XposedHelpers.callMethod(response, "\u037f")
                        }.getOrNull() ?: return
                        filterViewLayerCards(dto, "search result") { card ->
                            isSearchResultRecommendation(card)
                        }
                    }
                },
            )
            check(handles.isNotEmpty()) { "Base card response method was not found" }
            HookLog.i(TAG, "App Market C17 search-result recommendation boundary hooked")
        }.onFailure { error ->
            HookLog.w(TAG, "Failed to hook App Market search-result recommendations", error)
        }
    }

/** Search response filtering before DTOs reach child pages or cached adapters. */
    private fun installSearchResultResponseHooks(classLoader: ClassLoader) {
        val responseClass = XposedHelpers.findClassIfExists(PAGING_RESPONSE_CLASS, classLoader)
            ?: run {
                HookLog.w(TAG, "App Market paging response class was not found")
                return
            }
        var installed = 0
        SEARCH_RESULT_RESPONSE_CLASSES.forEach { className ->
            val targetClass = XposedHelpers.findClassIfExists(className, classLoader)
                ?: return@forEach
            targetClass.declaredMethods
                .filter { method -> method.parameterTypes.contains(responseClass) }
                .forEach { target ->
                    runCatching {
                        XposedBridge.hookMethod(
                            target,
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam) {
                                    if (!LspConfig
                                            .isAppMarketHideSearchResultRecommendationsEnabledXposed()
                                    ) {
                                        return
                                    }
                                    val response = param.args.firstOrNull {
                                        responseClass.isInstance(it)
                                    } ?: return
                                    val dto = runCatching {
                                        XposedHelpers.callMethod(response, "\u037f")
                                    }.getOrNull() ?: return
                                    filterViewLayerCards(dto, "search result relay") { card ->
                                        isSearchResultRecommendation(card)
                                    }
                                }
                            },
                        )
                        installed++
                    }.onFailure { error ->
                        HookLog.w(TAG, "Failed to hook $className search response relay", error)
                    }
                }
        }
        if (installed == 0) {
            HookLog.w(TAG, "No App Market search-result response relays were hooked")
        } else {
            HookLog.i(TAG, "App Market C17 search-result response relays hooked ($installed)")
        }
    }

    /** Clear detail recommendation content while preserving C17's native Recommend page and tabs. */
    private fun installDetailRecommendationHook(classLoader: ClassLoader) {
        installDetailResponseHook(classLoader)
        installDetailRecommendPageContentHook(classLoader)
        installDetailFinalCardAdapterHooks(classLoader)

        val dtoClass = XposedHelpers.findClassIfExists(VIEW_LAYER_WRAP_DTO_CLASS, classLoader)
        val detailContentClass = XposedHelpers.findClassIfExists(
            DETAIL_CONTENT_VIEW_CLASS,
            classLoader,
        )
        if (dtoClass == null || detailContentClass == null) {
            HookLog.w(TAG, "App Market detail recommendation data classes were not found")
        } else {
            runCatching {
                val targets = detailContentClass.declaredMethods.filter { method ->
                    method.returnType == Void.TYPE && method.parameterTypes.contains(dtoClass)
                }
                check(targets.isNotEmpty()) { "Detail card submit methods were not found" }
                targets.forEach { target ->
                    XposedBridge.hookMethod(
                        target,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                if (!LspConfig
                                        .isAppMarketHideDetailRecommendationsEnabledXposed()
                                ) {
                                    return
                                }
                                val dto = param.args.firstOrNull { dtoClass.isInstance(it) } ?: return
                                filterViewLayerCards(dto, "app detail") { card ->
                                    isDetailRecommendation(card)
                                }
                            }
                        },
                    )
                }
                HookLog.i(TAG, "App Market C17 detail recommendation data boundary hooked")
            }.onFailure { error ->
                HookLog.w(TAG, "Failed to hook App Market detail recommendation data", error)
            }
        }
    }

    /** Empty the Recommend page's own payload without changing its tab or native tab animation. */
    private fun installDetailRecommendPageContentHook(classLoader: ClassLoader) {
        val contentClass = XposedHelpers.findClassIfExists(
            DETAIL_RECOMMEND_CONTENT_VIEW_CLASS,
            classLoader,
        )
        val payloadClass = XposedHelpers.findClassIfExists(
            DETAIL_RECOMMEND_PAYLOAD_CLASS,
            classLoader,
        )
        val dtoClass = XposedHelpers.findClassIfExists(VIEW_LAYER_WRAP_DTO_CLASS, classLoader)
        if (contentClass == null || payloadClass == null || dtoClass == null) {
            HookLog.w(TAG, "App Market detail Recommend page data classes were not found")
            return
        }
        val dtoGetter = payloadClass.declaredMethods.singleOrNull { method ->
            method.parameterTypes.isEmpty() && method.returnType == dtoClass
        } ?: run {
            HookLog.w(TAG, "App Market detail Recommend payload getter was not found uniquely")
            return
        }
        dtoGetter.isAccessible = true
        val targets = contentClass.declaredMethods.filter { method ->
            method.returnType == Void.TYPE &&
                method.parameterTypes.contentEquals(arrayOf(payloadClass))
        }
        if (targets.isEmpty()) {
            HookLog.w(TAG, "App Market detail Recommend content receiver was not found")
            return
        }
        targets.forEach { target ->
            runCatching {
                XposedBridge.hookMethod(
                    target,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (!LspConfig
                                    .isAppMarketHideDetailRecommendationsEnabledXposed()
                            ) {
                                return
                            }
                            val payload = param.args.firstOrNull() ?: return
                            val dto = runCatching { dtoGetter.invoke(payload) }.getOrNull() ?: return
                            filterViewLayerCards(dto, "detail Recommend page") { true }
                        }
                    },
                )
            }.onFailure { error ->
                HookLog.w(TAG, "Failed to hook App Market detail Recommend content", error)
            }
        }
        HookLog.i(TAG, "App Market C17 detail Recommend page content boundary hooked")
    }

    /**
     * Detail cards are appended straight to the card adapter on several C17 async paths. This is
     * the last shared boundary before they become rows, so it also covers cached detail payloads.
     */
    private fun installDetailFinalCardAdapterHooks(classLoader: ClassLoader) {
        var installed = 0
        DETAIL_CARD_ADAPTER_CLASSES.forEach { className ->
            val adapterClass = XposedHelpers.findClassIfExists(className, classLoader)
                ?: return@forEach
            adapterClass.declaredMethods
                .filter { method ->
                    method.name == "addDataAndNotifyChanged" &&
                        method.parameterTypes.size == 1 &&
                        List::class.java.isAssignableFrom(method.parameterTypes[0])
                }
                .forEach { target ->
                    runCatching {
                        XposedBridge.hookMethod(
                            target,
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam) {
                                    if (!LspConfig
                                            .isAppMarketHideDetailRecommendationsEnabledXposed()
                                    ) {
                                        return
                                    }
                                    val callStack = Thread.currentThread().stackTrace
                                    val fromRecommendPage = callStack.any { frame ->
                                        frame.className == DETAIL_RECOMMEND_CONTENT_VIEW_CLASS
                                    }
                                    if (fromRecommendPage) {
                                        val cards = param.args.firstOrNull() as? List<*> ?: return
                                        if (cards.isNotEmpty()) {
                                            HookLog.i(
                                                TAG,
                                                "Cleared ${cards.size} detail Recommend page card(s)",
                                            )
                                        }
                                        param.args[0] = ArrayList<Any?>()
                                        return
                                    }
                                    val fromDetail = callStack.any { frame ->
                                        frame.className == DETAIL_CONTENT_VIEW_CLASS
                                    }
                                    if (!fromDetail) return
                                    val cards = param.args.firstOrNull() as? List<*> ?: return
                                    param.args[0] = filterCards(
                                        cards = cards,
                                        source = "app detail final adapter",
                                    ) { card ->
                                        isDetailRecommendation(card)
                                    }
                                }
                            },
                        )
                        installed++
                    }.onFailure { error ->
                        HookLog.w(TAG, "Failed to hook $className detail card adapter", error)
                    }
                }
        }
        if (installed == 0) {
            HookLog.w(TAG, "App Market detail final card adapter methods were not found")
        }
    }

    private fun installDetailResponseHook(classLoader: ClassLoader) {
        val repoClass = XposedHelpers.findClassIfExists(DETAIL_REPO_CLASS, classLoader)
        val responseClass = XposedHelpers.findClassIfExists(NETWORK_RESPONSE_CLASS, classLoader)
        if (repoClass == null || responseClass == null) {
            HookLog.w(TAG, "App Market detail response classes were not found")
            return
        }
        val targets = repoClass.declaredMethods.filter { method ->
            method.parameterTypes.firstOrNull() == responseClass
        }
        if (targets.isEmpty()) {
            HookLog.w(TAG, "App Market detail response methods were not found")
            return
        }
        targets.forEach { target ->
            runCatching {
                XposedBridge.hookMethod(
                    target,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (!LspConfig.isAppMarketHideDetailRecommendationsEnabledXposed()) {
                                return
                            }
                            val response = param.args.firstOrNull() ?: return
                            val dto = runCatching {
                                XposedHelpers.callMethod(response, "\u037f")
                            }.getOrNull() ?: return
                            filterViewLayerCards(dto, "app detail response") { card ->
                                isDetailRecommendation(card)
                            }
                        }
                    },
                )
            }.onFailure { error ->
                HookLog.w(TAG, "Failed to hook App Market detail response method", error)
            }
        }
        HookLog.i(TAG, "App Market C17 detail network response boundary hooked")
    }

    private fun findCardAdapterContext(presenter: Any): Any? {
        var type: Class<*>? = presenter.javaClass
        while (type != null) {
            val adapterField = type.declaredFields.firstOrNull { field ->
                field.type.name == CARD_API_ADAPTER_CLASS
            }
            if (adapterField != null) {
                return runCatching {
                    adapterField.isAccessible = true
                    val adapter = adapterField.get(presenter) ?: return@runCatching null
                    XposedHelpers.callMethod(adapter, "getContext")
                }.getOrNull()
            }
            type = type.superclass
        }
        return null
    }

    private fun filterViewLayerCards(
        dto: Any,
        source: String,
        shouldRemove: (Any?) -> Boolean,
    ) {
        val cards = runCatching {
            XposedHelpers.callMethod(dto, "getCards") as? List<*>
        }.getOrNull()
        if (cards != null) {
            val filtered = filterCards(cards, source, shouldRemove)
            if (filtered !== cards) {
                XposedHelpers.callMethod(dto, "setCards", filtered)
            }
        }

        // Search can deliver a secondary recommendation card in ViewFoot. Filtering it here
        // prevents a floating/late recommendation from reappearing after the main list is clean.
        val foot = runCatching {
            XposedHelpers.callMethod(dto, "getViewFoot")
        }.getOrNull() ?: return
        val footCards = runCatching {
            XposedHelpers.callMethod(foot, "getCards") as? List<*>
        }.getOrNull() ?: return
        val filteredFoot = filterCards(footCards, "$source foot", shouldRemove)
        if (filteredFoot !== footCards) {
            XposedHelpers.callMethod(foot, "setCards", filteredFoot)
        }
    }

    private fun filterCards(
        cards: List<*>,
        source: String,
        shouldRemove: (Any?) -> Boolean,
    ): List<*> {
        val removed = cards.filter(shouldRemove)
        if (removed.isEmpty()) return cards
        HookLog.i(
            TAG,
            "Removed ${removed.size} $source recommendation card(s): " +
                removed.joinToString { cardSignature(it) },
        )
        return ArrayList(cards.filterNot(shouldRemove))
    }

    private fun isSearchResultRecommendation(card: Any?): Boolean {
        if (cardCode(card) in SEARCH_RESULT_RECOMMENDATION_CODES) return true
        if (cardKey(card) in SEARCH_RESULT_RECOMMENDATION_KEYS) return true
        return cardTitle(card).containsAnyMarker(SEARCH_RECOMMENDATION_TITLE_MARKERS)
    }

    private fun isSearchHomeRecommendation(card: Any?): Boolean {
        if (cardCode(card) in SEARCH_HOME_RECOMMENDATION_CODES) return true
        return cardTitle(card).containsAnyMarker(SEARCH_RECOMMENDATION_TITLE_MARKERS)
    }

    private fun isDetailRecommendation(card: Any?): Boolean {
        if (cardCode(card) in DETAIL_RECOMMENDATION_CODES) return true
        if (cardKey(card) in DETAIL_RECOMMENDATION_KEYS) return true
        return cardTitle(card).containsAnyMarker(DETAIL_RECOMMENDATION_TITLE_MARKERS)
    }

    private fun String?.containsAnyMarker(markers: List<String>): Boolean {
        val normalized = this?.replace(" ", "")?.lowercase(Locale.ROOT) ?: return false
        return markers.any { marker -> normalized.contains(marker.replace(" ", "").lowercase(Locale.ROOT)) }
    }

    private fun cardCode(card: Any?): Int? = cardInt(card, "getCode")

    private fun cardKey(card: Any?): Int? = cardInt(card, "getKey")

    private fun cardInt(card: Any?, methodName: String): Int? = card?.let {
        runCatching {
            (XposedHelpers.callMethod(it, methodName) as? Number)?.toInt()
        }.getOrNull()
    }

    private fun cardTitle(card: Any?): String? = card?.let {
        runCatching {
            XposedHelpers.callMethod(it, "getTitle") as? String
        }.getOrNull()
    }

    private fun cardSignature(card: Any?): String {
        if (card == null) return "null"
        return "${card.javaClass.simpleName}(code=${cardCode(card)}, key=${cardKey(card)}, " +
            "title=${cardTitle(card)})"
    }

    /** Keep the native account/service header card and discard following recommendation cards. */
    private fun installMineRecommendationHook(classLoader: ClassLoader) {
        val mineFragmentClass = XposedHelpers.findClassIfExists(MINE_FRAGMENT_CLASS, classLoader)
            ?: run {
                HookLog.w(TAG, "App Market MineFragment was not found")
                return
            }
        val viewLayerWrapDtoClass = XposedHelpers.findClassIfExists(
            VIEW_LAYER_WRAP_DTO_CLASS,
            classLoader,
        ) ?: run {
            HookLog.w(TAG, "App Market ViewLayerWrapDto was not found")
            return
        }
        val target = mineFragmentClass.declaredMethods.singleOrNull { method ->
            method.parameterTypes.size == 2 &&
                method.parameterTypes[0] == viewLayerWrapDtoClass &&
                method.parameterTypes[1] == Boolean::class.javaPrimitiveType &&
                method.returnType == Void.TYPE
        } ?: run {
            HookLog.w(TAG, "App Market MineFragment card method was not found uniquely")
            return
        }

        runCatching {
            XposedBridge.hookMethod(
                target,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!LspConfig.isAppMarketRemoveMineRecommendationsEnabledXposed()) return
                        val dto = param.args.firstOrNull() ?: return
                        val cards = XposedHelpers.callMethod(dto, "getCards") as? List<*>
                            ?: return
                        if (cards.size <= 1) return
                        XposedHelpers.callMethod(dto, "setCards", ArrayList(cards.take(1)))
                    }
                },
            )
            HookLog.i(TAG, "App Market C17 Mine recommendation hook installed")
        }.onFailure { error ->
            HookLog.w(TAG, "Failed to hook App Market Mine recommendations", error)
        }
    }

/** Region-rejection Activity handler that resumes the SDK success callback. */
    private fun installRejectedSwitchActivityFallback(
        classLoader: ClassLoader,
        checkerClass: Class<*>,
        resultClass: Class<*>,
    ) {
        val tipsActivityClass = XposedHelpers.findClassIfExists(
            SWITCH_TIPS_ACTIVITY_CLASS,
            classLoader,
        ) ?: run {
            HookLog.w(TAG, "App Market switch rejection Activity was not found")
            return
        }
        val runtimeClass = XposedHelpers.findClassIfExists(SWITCH_RUNTIME_CLASS, classLoader)
            ?: run {
                HookLog.w(TAG, "App Market switch runtime was not found")
                return
            }
        val managerClass = XposedHelpers.findClassIfExists(SWITCH_UI_MANAGER_CLASS, classLoader)
            ?: run {
                HookLog.w(TAG, "App Market switch UI manager was not found")
                return
            }

        runCatching {
            val handles = XposedBridge.hookAllMethods(
                tipsActivityClass,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!LspConfig.isAppMarketRegionRestrictionBypassEnabledXposed()) return
                        val activity = param.thisObject as? Activity ?: return
                        val targetName = activity.intent
                            ?.getStringExtra(EXTRA_TARGET_REGION)
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                            ?: return

                        runCatching {
                            val runtime = XposedHelpers.callStaticMethod(runtimeClass, "\u052b")
                                ?: error("Region switch runtime is null")
                            val regions = XposedHelpers.getObjectField(runtime, "\u0529") as? Map<*, *>
                            val targetCode = regions
                                ?.entries
                                ?.firstOrNull { (_, name) -> name?.toString() == targetName }
                                ?.key
                                ?.toString()
                                ?.trim()
                                ?.uppercase(Locale.ROOT)
                                ?.takeIf { it.matches(Regex("[A-Z]{2}")) }
                                ?: error("Unable to resolve region code for $targetName")
                            val originalRegion = runCatching {
                                XposedHelpers.callStaticMethod(
                                    checkerClass,
                                    "\u058f",
                                    activity,
                                ) as? String
                            }.getOrNull().orEmpty()

                            val successResult = XposedHelpers.newInstance(resultClass)
                            XposedHelpers.callMethod(successResult, "\u052f", 0)
                            XposedHelpers.callMethod(successResult, "\u0588", "User selected region")
                            XposedHelpers.callMethod(
                                successResult,
                                "\u0560",
                                HashMap<String, String>().apply {
                                    put("selectedRegionCode", targetCode)
                                    put("selectedRegionName", targetName)
                                    put("originalRegion", originalRegion)
                                },
                            )

                            XposedHelpers.callMethod(runtime, "\u052e", successResult)
                            XposedHelpers.callStaticMethod(managerClass, "\u0528")
                            XposedHelpers.callMethod(runtime, "\u0528")
                            HookLog.i(
                                TAG,
                                "Converted rejected App Market switch to $targetCode into native success flow",
                            )
                        }.onFailure { error ->
                            HookLog.w(TAG, "Failed to convert rejected App Market switch", error)
                        }
                    }
                },
            )
            check(handles.isNotEmpty()) { "Switch rejection Activity onCreate was not found" }
            HookLog.i(
                TAG,
                "App Market rejection Activity fallback hooked " +
                    "(enabled=${LspConfig.isAppMarketRegionRestrictionBypassEnabledXposed()})",
            )
        }.onFailure { error ->
            HookLog.w(TAG, "Failed to hook App Market rejection Activity fallback", error)
        }
    }
}
