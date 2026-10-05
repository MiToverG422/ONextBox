package com.mi.onextbox.lsp

import android.content.ContentResolver
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import com.mi.onextbox.lsp.compat.ModernHookBridge as XposedBridge
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernMethodHook as XC_MethodHook
import com.mi.onextbox.lsp.compat.ModernReflect as XposedHelpers
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object OosLocalizerHooker {
    private const val TAG = "ONextBox-Localizer"
    private const val APP_FEATURE_PROVIDER_AUTHORITY =
        "com.oplus.customize.coreapp.configmanager.configprovider.AppFeatureProvider"

    private val supportedPackages = setOf(
        "com.android.contacts",
        "com.android.incallui",
        "com.android.launcher",
        "com.coloros.assistantscreen",
        "com.coloros.colordirectservice",
        "com.coloros.phonemanager",
        "com.coloros.sceneservice",
        "com.coloros.translate",
        "com.google.android.webview",
        "com.heytap.cloud",
        "com.heytap.market",
        "com.heytap.mcs",
        "com.heytap.mydevices",
        "com.heytap.openid",
        "com.heytap.speechassist",
        "com.oplus.aimemory",
        "com.oplus.aiunit",
        "com.oplus.aiwriter",
        "com.oplus.cosa",
        "com.oplus.deepthinker",
        "com.oplus.dmp",
        "com.oplus.linker",
        "com.oplus.metis",
        "com.oplus.pantanal.ums",
        "com.oplus.travelengine",
        "com.oppo.quicksearchbox",
        "com.coloros.shortcuts",
        "com.coloros.ocrscanner",
        "com.coloros.weather2",
        "com.coloros.weather.service",
        "com.oplus.pay",
        "com.nearme.instant.platform",
        "com.oplus.member",
        "com.heytap.tas",
        "com.android.mms",
        "com.finshell.wallet",
        "com.oplus.account",
        "com.oplus.vip",
        "com.android.server.telecom",
        "com.oplus.callrecorder",
        "com.coloros.findmyphone",
        "com.coloros.smartsidebar",
        "com.ted.number",
        "com.oplus.phonenoareainquire",
        "com.nearme.gamecenter",
        "com.oplus.games",
        "com.android.phone",
        "com.oplus.blacklistapp",
        "com.oplus.aiplaymate",
        "com.oplus.aipaint",
        "com.oplus.aicall",
        "com.coloros.accessibilityassistant",
        "com.oplus.audiomonitor",
        "com.oppo.instant.local.service"
    )

    private val keyMethodClassNames = listOf(
        "com.android.common.util.AppFeatureUtils",
        "com.oplus.coreapp.appfeature.AppFeatureProviderUtils"
    )

    private val staticPropertyRules = mapOf(
        "ro.oplus.image.system_ext.area" to "domestic",
        "ro.oplus.image.my_stock.type" to "domestic_OPPO",
        "ro.build.display.id" to "PMA120_16.0.7.210(CN01)",
        "ro.build.display.full_id" to
            "PMA120domestic_11_16.0.7.210(CN01)_2026051318470000",
        "ro.build.version.ota" to "PMA120_11.A.45_0450_202605131847",
        "ro.oplus.image.my_manifest.version" to
            "PMA120_11.A.45_0450_202605131847.97.41d84fe6",
        "ro.build.display.ota" to "PMA120_11_A.45",
        "ro.product.authentication" to "26C44PC2V997",
        "persist.bluetooth.airpods_support" to "true"
    )

    private val appFeatureExistsRuleKeys = setOf(
        "com.android.incallui.region_cn",
        "com.android.launcher.CN_VERSION",
        "com.android.settings.cn_version",
        "com.oplusos.deepthinker.cn.enable"
    )

    private val staticAppFeatureValueRuleKeys = setOf(
        "com.oplus.aiwriter.main_host_address",
        "com.oplus.smartanalysis.rule_server_host"
    )

    private val propertyRuleKeys = staticPropertyRules.keys + setOf(
        "persist.sys.oplus.region",
        "ro.oplus.pipeline.region",
        "ro.vendor.oplus.regionmark",
        "persist.sys.oppo.region",
        "user.region",
        "ro.vendor.oplus.version",
        "ro.product.locale",
        "persist.sys.locale",
        "ro.product.name",
        "ro.product.model"
    )

    private data class RuntimeConfig(
        val localizerEnabled: Boolean = false,
        val packageEnabled: Boolean = true,
        val customConfig: Boolean = false,
        val region: String = LspConfig.DEFAULT_OOS_LOCALIZER_REGION,
        val locale: String = LspConfig.DEFAULT_OOS_LOCALIZER_LOCALE,
        val model: String = LspConfig.DEFAULT_OOS_LOCALIZER_MODEL,
        val featureStates: Map<String, Boolean> = LspConfig.OOS_LOCALIZER_FEATURE_DEFAULTS,
        val propertyValues: Map<String, String> = staticPropertyRules,
        val appFeatureValues: Map<String, String> = LspConfig.OOS_LOCALIZER_APP_FEATURE_DEFAULTS,
    ) {
        fun isFeatureEnabled(feature: String): Boolean =
            featureStates[feature] ?: LspConfig.OOS_LOCALIZER_FEATURE_DEFAULTS[feature] ?: true
    }

    private val installedHookKeys = ConcurrentHashMap.newKeySet<String>()
    private val internalCallInProgress = ThreadLocal.withInitial { false }
    @Volatile
    private var runtimeConfig = RuntimeConfig()

    val supportedPackageNames: Set<String>
        get() = supportedPackages

    fun isSupportedPackage(packageName: String): Boolean = packageName in supportedPackages

    fun hook(packageName: String, classLoader: ClassLoader?) {
        if (!isSupportedPackage(packageName)) return
        val config = captureRuntimeConfig(packageName)
        runtimeConfig = config
        val localizerEnabled = config.localizerEnabled
        val packageScopeEnabled = config.packageEnabled
        if (!localizerEnabled || !packageScopeEnabled) return
        log("install hooks for $packageName")
        if (config.isFeatureEnabled(LspConfig.OOS_LOCALIZER_FEATURE_APP_FEATURES)) {
            hookAppFeatureResolverQuery()
            hookAppFeatureKeyMethods(classLoader)
        }
        if (localizerEnabled) {
            val buildModelEnabled =
                config.isFeatureEnabled(LspConfig.OOS_LOCALIZER_FEATURE_BUILD_MODEL)
            val localeEnabled =
                config.isFeatureEnabled(LspConfig.OOS_LOCALIZER_FEATURE_LOCALE)
            val propertyHookNeeded = buildModelEnabled ||
                localeEnabled ||
                config.isFeatureEnabled(LspConfig.OOS_LOCALIZER_FEATURE_REGION) ||
                config.isFeatureEnabled(LspConfig.OOS_LOCALIZER_FEATURE_PROPERTIES)
            if (buildModelEnabled) {
                spoofBuildModel(packageName)
            }
            if (localeEnabled) {
                spoofDefaultLocale(packageName)
            }
            if (propertyHookNeeded) {
                hookSystemProperties(classLoader)
                hookJavaSystemProperties()
            }
        }
    }

    /**
     * Captured once per target process. Localizer settings require the affected app to restart,
     * so hook callbacks can safely use this immutable snapshot instead of reloading preferences
     * from the SystemProperties.get hot path.
     */
    private fun captureRuntimeConfig(packageName: String): RuntimeConfig {
        val localizerEnabled = LspConfig.isOosLocalizerEnabledXposed()
        val customConfig = localizerEnabled &&
            LspConfig.getOosLocalizerConfigModeXposed() == LspConfig.OOS_LOCALIZER_CONFIG_CUSTOM
        val featureStates = if (localizerEnabled) {
            LspConfig.OOS_LOCALIZER_FEATURE_DEFAULTS.keys.associateWith { feature ->
                LspConfig.isOosLocalizerFeatureEnabledXposed(feature)
            }
        } else {
            LspConfig.OOS_LOCALIZER_FEATURE_DEFAULTS
        }
        val propertyValues = if (customConfig) {
            LspConfig.OOS_LOCALIZER_PROPERTY_DEFAULTS.mapValues { (key, defaultValue) ->
                LspConfig.getOosLocalizerPropertyXposed(key) ?: defaultValue
            }
        } else {
            staticPropertyRules
        }
        val appFeatureValues = if (localizerEnabled) {
            LspConfig.OOS_LOCALIZER_APP_FEATURE_DEFAULTS.mapValues { (key, defaultValue) ->
                LspConfig.getOosLocalizerAppFeatureXposed(key) ?: defaultValue
            }
        } else {
            LspConfig.OOS_LOCALIZER_APP_FEATURE_DEFAULTS
        }
        return RuntimeConfig(
            localizerEnabled = localizerEnabled,
            packageEnabled = !customConfig || LspConfig.isOosLocalizerPackageEnabledXposed(packageName),
            customConfig = customConfig,
            region = if (customConfig) {
                LspConfig.getOosLocalizerRegionXposed()
            } else {
                LspConfig.DEFAULT_OOS_LOCALIZER_REGION
            },
            locale = if (customConfig) {
                LspConfig.getOosLocalizerLocaleXposed()
            } else {
                LspConfig.DEFAULT_OOS_LOCALIZER_LOCALE
            },
            model = if (customConfig) {
                LspConfig.getOosLocalizerModelXposed()
            } else {
                LspConfig.DEFAULT_OOS_LOCALIZER_MODEL
            },
            featureStates = featureStates,
            propertyValues = propertyValues,
            appFeatureValues = appFeatureValues,
        )
    }

    private fun spoofBuildModel(packageName: String) {
        val value = localizerModel()
        runCatching {
            val field = Build::class.java.getDeclaredField("MODEL")
            if (field.get(null) is String && setStaticStringField(field, value)) {
                log("spoof $packageName android.os.Build.MODEL -> $value")
            }
        }.onFailure { error ->
            log("spoof android.os.Build.MODEL failed: ${error.javaClass.simpleName}")
        }
    }

    private fun spoofDefaultLocale(packageName: String) {
        runCatching {
            Locale.setDefault(Locale.forLanguageTag(localizerLocale()))
        }.onFailure { error ->
            log("spoof $packageName default locale failed: ${error.javaClass.simpleName}")
        }
    }

    private fun hookSystemProperties(classLoader: ClassLoader?) {
        hookMatchingMethods(
            className = "android.os.SystemProperties",
            classLoader = null,
            methodName = "get",
            returnType = String::class.java
        ) { method ->
            hookPropertyMethod(method, "SystemProperties.get")
        }
        hookMatchingMethods(
            className = "android.os.SystemProperties",
            classLoader = null,
            methodName = "native_get",
            returnType = String::class.java
        ) { method ->
            hookPropertyMethod(method, "SystemProperties.native_get")
        }
        hookMatchingMethods(
            className = "com.oplus.wrapper.os.SystemProperties",
            classLoader = classLoader,
            methodName = "get",
            returnType = String::class.java
        ) { method ->
            hookPropertyMethod(method, "wrapper SystemProperties.get")
        }
    }

    private fun hookPropertyMethod(method: Method, source: String) {
        hookFastMethod(
            method = method,
            feature = "property|$source",
            hooker = XposedInterface.Hooker { chain ->
                val key = chain.args.firstOrNull() as? String
                val value = key?.let(::propertyRuleValue)
                value ?: chain.proceed()
            }
        )
    }

    private fun hookJavaSystemProperties() {
        System::class.java.declaredMethods
            .filter { it.name == "getProperty" && it.returnType == String::class.java }
            .forEach { method ->
                hookPropertyMethod(method, "System.getProperty")
            }
    }

    private fun hookAppFeatureResolverQuery() {
        ContentResolver::class.java.declaredMethods
            .filter { it.name == "query" && Cursor::class.java.isAssignableFrom(it.returnType) }
            .forEach { method ->
                hookFastMethod(
                    method = method,
                    feature = "app_feature_query",
                    hooker = XposedInterface.Hooker { chain ->
                        val uri = chain.args.firstOrNull() as? Uri
                        val originalResult = chain.proceed()
                        if (uri?.authority != APP_FEATURE_PROVIDER_AUTHORITY) {
                            originalResult
                        } else {
                            val cursor = originalResult as? Cursor
                            if (cursor == null) {
                                originalResult
                            } else {
                                overrideCursor(queryInfo(chain.args), cursor) ?: originalResult
                            }
                        }
                    }
                )
            }
    }

    private fun hookAppFeatureKeyMethods(classLoader: ClassLoader?) {
        keyMethodClassNames.forEach { className ->
            val hookClass = XposedHelpers.findClassIfExists(className, classLoader) ?: return@forEach
            hookClass.declaredMethods
                .filter { canOverrideAppFeatureMethod(it) }
                .forEach { method ->
                    hookMethod(method, "app_feature_method") {
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                overrideAppFeatureReturn(method, param)
                            }
                        }
                    }
                }
        }
    }

    private fun overrideAppFeatureReturn(method: Method, param: XC_MethodHook.MethodHookParam) {
        val featureName = param.args.firstOrNull { it is String } as? String ?: return
        if (!isConcreteFeatureName(featureName)) return

        val type = if (method.returnType == Boolean::class.javaPrimitiveType ||
            method.returnType == Boolean::class.javaObjectType ||
            param.result is Boolean
        ) {
            "app_feature_exists"
        } else {
            "app_feature"
        }
        val value = decision(type, featureName) ?: return
        val coerced = coerceReturnValue(value, method.returnType, param.result) ?: return
        param.result = coerced
    }

    private fun overrideCursor(
        queryInfo: QueryInfo,
        cursor: Cursor
    ): Cursor? {
        val queriedFeatureName = extractQueriedFeatureName(queryInfo)
        if (queriedFeatureName == null || !hasAppFeatureRule(queriedFeatureName)) return null

        return runCatching {
            val columnNames = cursor.columnNames
            val matrixCursor = MatrixCursor(columnNames)
            var rowCount = 0

            while (cursor.moveToNext()) {
                if (shouldSuppressRow(queryInfo, cursor)) {
                    continue
                } else {
                    val rowCopy = copyRow(cursor, columnNames)
                    matrixCursor.addRow(rowCopy.values)
                    rowCount++
                }
            }

            if (rowCount == 0) {
                createSyntheticRow(queryInfo, columnNames)?.let { rowCopy ->
                    matrixCursor.addRow(rowCopy.values)
                }
            }

            cursor.close()
            matrixCursor
        }.getOrElse { error ->
            log("override AppFeature cursor failed: ${error.javaClass.simpleName}")
            null
        }
    }

    private fun shouldSuppressRow(queryInfo: QueryInfo, cursor: Cursor): Boolean {
        val featureName = getCursorString(cursor, "featurename")
            ?.takeIf { isConcreteFeatureName(it) }
            ?: extractQueriedFeatureName(queryInfo)
            ?: return false
        val value = appFeatureExistsRule(featureName) ?: return false
        return !value
    }

    private fun createSyntheticRow(queryInfo: QueryInfo, columnNames: Array<String>): RowCopy? {
        val featureName = extractQueriedFeatureName(queryInfo) ?: return null
        val existsValue = appFeatureExistsRule(featureName)
        val featureValue = appFeatureValueRule(featureName)
        if (existsValue == false) return null
        if (existsValue == null && featureValue == null) return null

        val row = arrayOfNulls<Any>(columnNames.size)
        val valueColumn = columnNames.firstOrNull { it == "parameters" } ?: "lists"
        columnNames.forEachIndexed { index, name ->
            row[index] = when {
                name == "_id" -> 0L
                name == "featurename" -> featureName
                featureValue != null && name == valueColumn -> featureValue
                name == "parameters" || name == "lists" -> ""
                else -> null
            }
        }
        return RowCopy(row, true)
    }

    private fun copyRow(cursor: Cursor, columnNames: Array<String>): RowCopy {
        val row = arrayOfNulls<Any>(columnNames.size)
        val featureName = getCursorString(cursor, "featurename")
        val valueColumn = chooseValueColumn(cursor)
        val featureValue = featureName?.let { appFeatureValueRule(it) }
        var changed = false

        columnNames.forEachIndexed { index, name ->
            if (featureValue != null && name == valueColumn) {
                row[index] = featureValue
                changed = true
            } else {
                row[index] = getCursorValue(cursor, index)
            }
        }
        return RowCopy(row, changed)
    }

    private fun chooseValueColumn(cursor: Cursor): String {
        val parameters = getCursorString(cursor, "parameters")
        return if (!parameters.isNullOrEmpty()) "parameters" else "lists"
    }

    private fun getCursorString(cursor: Cursor, columnName: String): String? {
        val index = cursor.getColumnIndex(columnName)
        return if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null
    }

    private fun getCursorValue(cursor: Cursor, index: Int): Any? {
        if (cursor.isNull(index)) return null
        return when (cursor.getType(index)) {
            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
            Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(index)
            else -> cursor.getString(index)
        }
    }

    private fun queryInfo(args: Array<Any?>): QueryInfo = queryInfo(args.asList())

    private fun queryInfo(args: List<Any?>): QueryInfo {
        val queryArgs = args.getOrNull(2)
        if (queryArgs is Bundle) {
            return QueryInfo(
                selection = queryArgs.getString("android:query-arg-sql-selection"),
                selectionArgs = queryArgs.getStringArray("android:query-arg-sql-selection-args"),
                queryArgs = queryArgs
            )
        }
        val selectionArgs = args.getOrNull(3)
        return QueryInfo(
            selection = queryArgs as? String,
            selectionArgs = (selectionArgs as? Array<*>)?.filterIsInstance<String>()?.toTypedArray(),
            queryArgs = null
        )
    }

    private fun extractQueriedFeatureName(queryInfo: QueryInfo): String? {
        val selection = queryInfo.selection
        return if (selection is String && selection.contains("featurename")) {
            extractFeatureName(selection, queryInfo.selectionArgs).takeIf { isConcreteFeatureName(it) }
        } else {
            null
        }
    }

    private fun extractFeatureName(selection: Any?, selectionArgs: Any?): String {
        if (selectionArgs is Array<*> && selectionArgs.isNotEmpty()) {
            val firstArg = selectionArgs[0] as? String
            if (!firstArg.isNullOrEmpty()) return firstArg
        }
        val rawSelection = selection as? String ?: return "<all>"
        val featureIndex = rawSelection.indexOf("featurename")
        val equalsIndex = rawSelection.indexOf('=', startIndex = featureIndex.coerceAtLeast(0))
        if (featureIndex < 0 || equalsIndex < 0 || equalsIndex + 1 >= rawSelection.length) {
            return rawSelection
        }
        val rawValue = rawSelection.substring(equalsIndex + 1).trim()
        if (rawValue.startsWith("?")) return "<selection-arg>"
        return stripSqlLiteral(rawValue)
    }

    private fun stripSqlLiteral(value: String): String {
        val andIndex = value.indexOf(" AND ")
        val trimmed = if (andIndex > 0) value.substring(0, andIndex).trim() else value
        return if ((trimmed.startsWith("'") && trimmed.endsWith("'")) ||
            (trimmed.startsWith("\"") && trimmed.endsWith("\""))
        ) {
            trimmed.substring(1, trimmed.length - 1)
        } else {
            trimmed
        }
    }

    private fun canOverrideAppFeatureMethod(method: Method): Boolean {
        val hasStringParameter = method.parameterTypes.any { it == String::class.java }
        val returnType = method.returnType
        val canOverrideReturn = returnType == String::class.java ||
            returnType == Boolean::class.javaPrimitiveType ||
            returnType == Boolean::class.javaObjectType ||
            returnType == Any::class.java
        return hasStringParameter && canOverrideReturn
    }

    private fun coerceReturnValue(value: String, returnType: Class<*>, originalResult: Any?): Any? {
        return if (returnType == Boolean::class.javaPrimitiveType ||
            returnType == Boolean::class.javaObjectType ||
            originalResult is Boolean
        ) {
            parseBooleanValue(value)
        } else if (returnType == String::class.java || originalResult is String || originalResult == null || returnType == Any::class.java) {
            value
        } else {
            null
        }
    }

    private fun hookMatchingMethods(
        className: String,
        classLoader: ClassLoader?,
        methodName: String,
        returnType: Class<*>?,
        onMethod: (Method) -> Unit
    ) {
        val hookClass = XposedHelpers.findClassIfExists(className, classLoader) ?: return
        hookClass.declaredMethods
            .filter { method ->
                method.name == methodName && (returnType == null || method.returnType == returnType)
            }
            .forEach(onMethod)
    }

    private fun hookMethod(method: Method, feature: String, hookFactory: () -> XC_MethodHook) {
        val key = "$feature|${method.declaringClass.name}|${method.toGenericString()}"
        if (!installedHookKeys.add(key)) return
        runCatching {
            method.isAccessible = true
            XposedBridge.hookMethod(method, hookFactory())
            log("hooked ${method.toGenericString()}")
        }.onFailure { error ->
            installedHookKeys.remove(key)
            log("hook failed: ${method.toGenericString()} (${error.javaClass.simpleName})")
        }
    }

    private fun hookFastMethod(
        method: Method,
        feature: String,
        hooker: XposedInterface.Hooker,
    ) {
        val key = "$feature|${method.declaringClass.name}|${method.toGenericString()}"
        if (!installedHookKeys.add(key)) return
        runCatching {
            ModernHookRegistry.installFast("localizer:$key", method, hooker)
            log("hooked ${method.toGenericString()}")
        }.onFailure { error ->
            installedHookKeys.remove(key)
            log("hook failed: ${method.toGenericString()} (${error.javaClass.simpleName})")
        }
    }

    private fun setStaticStringField(field: Field, value: String): Boolean {
        return runCatching {
            field.isAccessible = true
            clearFinalModifier(field)
            field.set(null, value)
            true
        }.getOrDefault(false)
    }

    private fun clearFinalModifier(field: Field) {
        runCatching {
            val modifiersField = Field::class.java.getDeclaredField("modifiers")
            modifiersField.isAccessible = true
            modifiersField.setInt(field, field.modifiers and Modifier.FINAL.inv())
        }
        runCatching {
            val accessFlagsField = Field::class.java.getDeclaredField("accessFlags")
            accessFlagsField.isAccessible = true
            accessFlagsField.setInt(field, accessFlagsField.getInt(field) and Modifier.FINAL.inv())
        }
    }

    private fun decision(type: String, key: String?): String? {
        val concreteKey = key ?: return null
        if (!isRecordableKey(concreteKey) || internalCallInProgress.get() == true) return null
        return when (type) {
            "property" -> if (runtimeConfig.localizerEnabled) propertyRuleValue(concreteKey) else null
            "build" -> if (runtimeConfig.localizerEnabled) buildRuleValue(concreteKey) else null
            "app_feature_exists" -> appFeatureExistsRule(concreteKey)?.toString()
            "app_feature" -> appFeatureValueRule(concreteKey)
            else -> null
        }
    }

    private fun hasAppFeatureRule(featureName: String): Boolean {
        return appFeatureExistsRule(featureName) != null ||
            appFeatureValueRule(featureName) != null
    }

    private fun appFeatureExistsRule(featureName: String): Boolean? {
        val config = runtimeConfig
        if (!config.localizerEnabled ||
            !config.isFeatureEnabled(LspConfig.OOS_LOCALIZER_FEATURE_APP_FEATURES)
        ) return null
        if (featureName !in appFeatureExistsRuleKeys) return null
        return config.appFeatureValues[featureName]
            ?.let(::parseBooleanValue)
    }

    private fun appFeatureValueRule(featureName: String): String? {
        val config = runtimeConfig
        if (!config.localizerEnabled ||
            !config.isFeatureEnabled(LspConfig.OOS_LOCALIZER_FEATURE_APP_FEATURES)
        ) return null
        return when (featureName) {
            "com.android.launcher.REGION_NAME",
            "com.oplus.aipaint.area" -> if (config.isFeatureEnabled(LspConfig.OOS_LOCALIZER_FEATURE_REGION)) {
                "String:${localizerRegion()}"
            } else {
                null
            }
            else -> if (featureName in staticAppFeatureValueRuleKeys) {
                config.appFeatureValues[featureName]
            } else {
                null
            }
        }
    }

    private fun propertyRuleValue(key: String): String? {
        if (key !in propertyRuleKeys) return null
        val config = runtimeConfig
        return when (key) {
            "persist.sys.oplus.region",
            "ro.oplus.pipeline.region",
            "ro.vendor.oplus.regionmark",
            "persist.sys.oppo.region",
            "user.region",
            "ro.vendor.oplus.version" -> if (config.isFeatureEnabled(LspConfig.OOS_LOCALIZER_FEATURE_REGION)) {
                localizerRegion()
            } else {
                null
            }
            "ro.product.locale",
            "persist.sys.locale" -> if (config.isFeatureEnabled(LspConfig.OOS_LOCALIZER_FEATURE_LOCALE)) {
                localizerLocale()
            } else {
                null
            }
            "ro.product.name",
            "ro.product.model" -> if (config.isFeatureEnabled(LspConfig.OOS_LOCALIZER_FEATURE_BUILD_MODEL)) {
                localizerModel()
            } else {
                null
            }
            else -> if (config.isFeatureEnabled(LspConfig.OOS_LOCALIZER_FEATURE_PROPERTIES)) {
                localizerProperty(key)
            } else {
                null
            }
        }
    }

    private fun buildRuleValue(key: String): String? {
        return when (key) {
            "MODEL" -> localizerModel()
            else -> null
        }
    }

    private fun localizerRegion(): String {
        return runtimeConfig.region
    }

    private fun localizerLocale(): String {
        return runtimeConfig.locale
    }

    private fun localizerModel(): String {
        return runtimeConfig.model
    }

    private fun localizerProperty(key: String): String? {
        return runtimeConfig.propertyValues[key]
    }

    private fun parseBooleanValue(value: String?): Boolean? {
        return when (value?.trim()?.lowercase()) {
            "true", "1", "yes", "on" -> true
            "false", "0", "no", "off" -> false
            else -> null
        }
    }

    private fun isRecordableKey(value: String?): Boolean {
        return !value.isNullOrEmpty() && !looksLikeArrayIdentity(value)
    }

    private fun isConcreteFeatureName(value: String?): Boolean {
        return !value.isNullOrEmpty() &&
            value != "<all>" &&
            value != "<selection-arg>" &&
            !looksLikeArrayIdentity(value)
    }

    private fun looksLikeArrayIdentity(value: String?): Boolean {
        if (value == null || value.length < 4 || value[0] != '[') return false
        val atIndex = value.indexOf('@')
        if (atIndex <= 1 || atIndex == value.lastIndex) return false
        return value.substring(atIndex + 1).all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    }

    private fun log(message: String) {
        internalCallInProgress.set(true)
        try {
            HookLog.i(TAG, message)
        } finally {
            internalCallInProgress.remove()
        }
    }

    private data class QueryInfo(
        val selection: Any?,
        val selectionArgs: Any?,
        val queryArgs: Any?
    )

    private data class RowCopy(
        val values: Array<Any?>,
        val changed: Boolean
    )
}
