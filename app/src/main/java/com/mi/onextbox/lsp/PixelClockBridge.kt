package com.mi.onextbox.lsp

import android.content.Context
import org.json.JSONObject
import java.lang.reflect.Method

internal class PixelClockBridge(private val hostLoader: ClassLoader) {
    private data class Provider(val instance: Any, val context: IsolatedClockContext, val create: Method)
    private val providers = mutableMapOf<String, Provider>()
    private val failed = mutableSetOf<String>()
    private val settingsType = type("com.android.systemui.plugins.keyguard.ui.clocks.ClockSettings")
    private val buffersType = type("com.android.systemui.plugins.keyguard.ui.clocks.ClockMessageBuffers")
    private val providerType = type("com.android.systemui.plugins.keyguard.ui.clocks.ClockProvider")
    private val controllerType = type("com.android.systemui.plugins.keyguard.ui.clocks.ClockController")
    private val getSeed = settingsType.getMethod("getSeedColor")
    private val getAxes = settingsType.getMethod("getAxes")
    private val copy = settingsType.getMethod("copy", String::class.java, Integer::class.java, getAxes.returnType)
    private var layoutResources: android.content.res.Resources? = null

    fun referenceResources(context: Context): android.content.res.Resources {
        layoutResources?.let { return it }
        val module = context.createPackageContext("com.mi.onextbox", 0)
        val catalog = module.assets.open("pixel-clocks/catalog.json").bufferedReader().use { JSONObject(it.readText()) }
        val entry = catalog.getJSONObject("DEFAULT")
        val file = ClockAssetStore.materialize(context, entry.getString("asset"), entry.getString("sha256"))
        return IsolatedClockContext(context, file, entry.getString("package"), hostLoader).resources.also { layoutResources = it }
    }

    @Synchronized
    fun create(context: Context, buffers: Any, original: Any): Any? {
        val id = AospUiSettings.clockXposed()
        if (id in failed) return null
        return try {
            val provider = providers.getOrPut(id) { load(context, buffers, id) }
            val settings = copy.invoke(original, id, getSeed.invoke(original), getAxes.invoke(original))
            val clock = checkNotNull(provider.create.invoke(provider.instance, context, settings))
            check(controllerType.isInstance(clock)) { "Clock controller ABI mismatch" }
            HookLog.i("ONextBox-AospClock", "Created $id using ${clock.javaClass.name}")
            clock
        } catch (error: Exception) {
            failed += id
            HookLog.w("ONextBox-AospClock", "Clock $id unavailable; using the host clock", error)
            null
        } catch (error: LinkageError) {
            failed += id
            HookLog.w("ONextBox-AospClock", "Clock $id ABI unavailable; using the host clock", error)
            null
        }
    }

    private fun load(context: Context, buffers: Any, id: String): Provider {
        val module = context.createPackageContext("com.mi.onextbox", 0)
        val catalog = module.assets.open("pixel-clocks/catalog.json").bufferedReader().use { JSONObject(it.readText()) }
        val entry = catalog.getJSONObject(id)
        val file = ClockAssetStore.materialize(context, entry.getString("asset"), entry.getString("sha256"))
        val loader = IsolatedClockLoader(file.absolutePath, hostLoader)
        val pluginContext = IsolatedClockContext(context, file, entry.getString("package"), loader)
        if (entry.optBoolean("default")) {
            val typeface = android.graphics.Typeface.Builder(pluginContext.assets, "fonts/GoogleSansFlexClock-Regular.ttf").build()
            checkNotNull(typeface) { "Clock font could not be loaded" }
            HostClassLookup.load("com.mi.onextbox.port.pixel.ClockTypeface", true, loader).getField("value").set(null, typeface)
        }
        val provider = HostClassLookup.load(entry.getString("provider"), true, loader).getDeclaredConstructor().newInstance()
        check(providerType.isInstance(provider)) { "Clock provider ABI mismatch" }
        if (entry.optBoolean("default")) {
            provider.javaClass.getField("resources").set(provider, pluginContext.resources)
            provider.javaClass.getField("vibrator").set(provider, context.getSystemService(android.os.Vibrator::class.java))
            val factoryType = HostClassLookup.load("com.android.systemui.shared.clocks.DefaultClockProvider\$\$ExternalSyntheticLambda0", true, loader)
            val constructor = factoryType.declaredConstructors.firstOrNull { it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType)) }
            val factory = if (constructor != null) constructor.apply { isAccessible = true }.newInstance(0)
                else factoryType.getDeclaredConstructor().apply { isAccessible = true }.newInstance().also {
                    factoryType.getDeclaredField("\$r8\$classId").apply { isAccessible = true }.setInt(it, 0)
                }
            provider.javaClass.getField("timeKeeperFactory").set(provider, factory)
        } else provider.javaClass.getMethod("onCreate", Context::class.java, Context::class.java)
            .invoke(provider, ClockStorageContext(context, entry.getString("package")), pluginContext)
        providerType.getMethod("initialize", buffersType).invoke(provider, buffers)
        val clocks = providerType.getMethod("getClocks").invoke(provider) as List<*>
        check(clocks.any { it != null && it.javaClass.getMethod("getClockId").invoke(it) == id }) { "Clock design was rejected by its provider" }
        return Provider(provider, pluginContext, providerType.getMethod("createClock", Context::class.java, settingsType))
    }

    private fun type(name: String) = HostClassLookup.load(name, false, hostLoader)
}
