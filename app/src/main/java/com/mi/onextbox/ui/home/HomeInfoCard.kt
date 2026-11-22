package com.mi.onextbox.ui.home

import android.content.Context
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.mi.onextbox.R
import com.mi.onextbox.ui.common.AppUiTokens
import com.mi.onextbox.ui.common.LocalPreviewTextPlaceholders
import com.mi.onextbox.ui.common.PreviewTextLine
import com.mi.onextbox.ui.common.TwemojiFlagIcon
import com.mi.onextbox.ui.common.countryFlagRanges
import com.mi.onextbox.ui.common.rememberCountryFlagGlyph
import com.mi.onextbox.ui.settings.SettingsDivider
import com.mi.onextbox.ui.settings.SettingsRowTextContent
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.theme.COUITheme
import java.util.Locale

@Composable
fun HomeInfoCard() {
    if (LocalPreviewTextPlaceholders.current) {
        val colors = COUITheme.colorScheme
        Card(modifier = Modifier.fillMaxWidth(), cornerRadius = AppUiTokens.CardCornerRadius) {
            repeat(7) { row ->
                if (row > 0) SettingsDivider()
                BasicComponent {
                    Column(Modifier.fillMaxWidth().height(38.dp)) {
                        PreviewTextLine((72 + row % 3 * 12).dp, 10.dp, colors.onSurface.copy(alpha = .75f))
                        Spacer(Modifier.height(9.dp))
                        PreviewTextLine((102 + row % 4 * 14).dp, 8.dp,
                            colors.onSurfaceVariantSummary.copy(alpha = .64f))
                    }
                }
            }
        }
        return
    }
    val context = LocalContext.current
    val systemVersion = Build.DISPLAY.ifBlank { Build.VERSION.INCREMENTAL }
    val kernelVersion = remember {
        System.getProperty("os.version")
            ?.lineSequence()
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
    }
    val regionText = remember(context) {
        detectHomeRegionText(context)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = AppUiTokens.CardCornerRadius,
    ) {
        Column {
            HomeRegionRow(
                label = stringResource(R.string.home_info_region),
                value = regionText,
            )
            SettingsDivider()
            HomeAndroidApiRow(Build.VERSION.RELEASE, Build.VERSION.SDK_INT)
            SettingsDivider()
            HomeInfoBasicRow(
                label = stringResource(R.string.info_device_model),
                value = Build.MODEL,
            )
            SettingsDivider()
            HomeInfoBasicRow(
                label = stringResource(R.string.home_info_system_version),
                value = systemVersion,
            )
            SettingsDivider()
            HomeInfoBasicRow(
                label = stringResource(R.string.home_info_system_architecture),
                value = Build.SUPPORTED_ABIS.joinToString(" / ")
                    .ifBlank { stringResource(R.string.home_info_unknown) },
            )
            SettingsDivider()
            HomeInfoBasicRow(
                label = stringResource(R.string.home_info_system_fingerprint),
                value = Build.FINGERPRINT.ifBlank { stringResource(R.string.home_info_unknown) },
            )
            SettingsDivider()
            HomeInfoBasicRow(
                label = stringResource(R.string.home_info_kernel_version),
                value = kernelVersion.ifBlank { stringResource(R.string.home_info_unknown) },
            )
        }
    }
}

@Composable
internal fun HomeRegionRow(label: String, value: String) {
    val range = remember(value) { countryFlagRanges(value).lastOrNull() }
    if (range == null) {
        HomeInfoBasicRow(label, value)
        return
    }
    val flag = value.substring(range.start, range.end)
    val summary = value.removeRange(range.start, range.end).trimEnd()
    val glyph = rememberCountryFlagGlyph(flag)
    HomeInfoTrailingArtworkRow(label, summary, glyph.aspectRatio) { TwemojiFlagIcon(glyph) }
}

@Composable
internal fun HomeAndroidApiRow(release: String, sdkInt: Int) {
    val label = stringResource(R.string.home_info_android_api)
    val value = stringResource(R.string.home_dash_android_api_format, release, sdkInt)
    val artwork = androidEasterEggArtwork(sdkInt)
    if (artwork == null) {
        HomeInfoBasicRow(label, value)
    } else {
        HomeInfoTrailingArtworkRow(label, value, 1f) {
            Image(painterResource(artwork), stringResource(R.string.home_android_easter_egg, sdkInt - 20),
                modifier = Modifier.testTag("homeAndroidEasterLogo"))
        }
    }
}

@Composable
private fun HomeInfoTrailingArtworkRow(
    label: String,
    summary: String,
    aspectRatio: Float,
    artwork: @Composable () -> Unit,
) {
    BasicComponent(modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Layout(
            modifier = Modifier.fillMaxWidth(),
            content = {
                Column { SettingsRowTextContent(title = label, summary = summary) }
                artwork()
            },
        ) { measurables, constraints ->
            val gap = 16.dp.roundToPx().coerceAtMost(constraints.maxWidth)
            val maxArtworkEdge = (constraints.maxWidth - gap) / 3
            val text = measurables[0].measure(
                constraints.copy(minWidth = 0, minHeight = 0, maxWidth = constraints.maxWidth - maxArtworkEdge - gap),
            )
            val artworkEdge = minOf(text.height, maxArtworkEdge)
            val artworkWidth = minOf(artworkEdge, (artworkEdge * aspectRatio).toInt())
            val artworkHeight = minOf(artworkEdge, (artworkEdge / aspectRatio).toInt())
            val icon = measurables[1].measure(Constraints.fixed(artworkWidth, artworkHeight))
            layout(constraints.maxWidth, text.height) {
                text.placeRelative(0, 0)
                icon.placeRelative(constraints.maxWidth - icon.width, (text.height - icon.height) / 2)
            }
        }
    }
}

internal fun detectHomeRegionText(context: Context): String {
    val nvid = readHomeSystemProperty("ro.build.oplus_nv_id")?.trim()
    val nvidRegion = nvid?.let { mapOplusNvidRegion(context, it) }
    if (nvidRegion != null) return nvidRegion

    val propertyRegion = listOf(
        "ro.oplus.regionmark",
        "ro.oplus.region",
        "ro.vendor.oplus.regionmark",
        "persist.sys.oplus.region",
        "ro.product.locale.region",
    ).asSequence()
        .mapNotNull(::readHomeSystemProperty)
        .map(String::trim)
        .firstOrNull(String::isNotEmpty)
        ?.let(::normalizeHomeRegionCode)

    if (propertyRegion != null) {
        return formatHomeRegion(context, propertyRegion)
    }

    if (!nvid.isNullOrBlank() && !nvid.equals("null", ignoreCase = true)) {
        return "NV $nvid"
    }

    val localeRegion = context.resources.configuration.locales[0].country
        .ifBlank { "XX" }
        .uppercase(Locale.ROOT)
    return formatHomeRegion(context, localeRegion)
}

private fun mapOplusNvidRegion(context: Context, nvid: String): String? = when (nvid) {
    "10010111" -> formatHomeRegion(context, "CN")
    "00011010" -> formatHomeRegion(context, "TW")
    "00110111" -> formatHomeRegion(context, "RU")
    "01000100" -> formatHomeRegion(context, "GDPR_EU")
    "10001101" -> formatHomeRegion(context, "GDPR_EUROPE")
    "00011011" -> formatHomeRegion(context, "IN")
    "00110011" -> formatHomeRegion(context, "ID")
    "00111000" -> formatHomeRegion(context, "MY")
    "00111001" -> formatHomeRegion(context, "TH")
    "00111110" -> formatHomeRegion(context, "PH")
    "10000011" -> formatHomeRegion(context, "SA")
    "10011010" -> formatHomeRegion(context, "LATAM")
    "10011110" -> formatHomeRegion(context, "BR")
    "10100110" -> formatHomeRegion(context, "MEA")
    else -> null
}

private fun normalizeHomeRegionCode(raw: String): String? {
    val normalized = raw
        .replace('-', '_')
        .substringAfterLast('_')
        .uppercase(Locale.ROOT)
    val mapped = when (normalized) {
        "INDIA" -> "IN"
        "CHINA" -> "CN"
        "HONGKONG", "HONG_KONG" -> "HK"
        "TAIWAN" -> "TW"
        "GLOBAL", "ROW", "WW", "EUEX" -> "GLO"
        else -> normalized
    }
    return mapped.takeIf { code ->
        code.length in 2..3 && code.all { it in 'A'..'Z' }
    }
}

private fun formatHomeRegion(context: Context, code: String): String {
    val normalized = code.uppercase(Locale.ROOT)
    val locale = context.resources.configuration.locales[0].let {
        if (it.language == "ja" && it.country == "CN") Locale.TRADITIONAL_CHINESE else it
    }
    return when (normalized) {
        "GDPR_EU", "EU" -> "GDPR ${Locale.Builder().setRegion("EU").build().getDisplayCountry(locale)} 🇪🇺"
        "GDPR_EUROPE" -> "GDPR ${context.getString(R.string.home_region_europe)} 🇪🇺"
        "LATAM" -> "LATAM ${context.getString(R.string.home_region_latam)}"
        "MEA", "ME" -> "MEA ${context.getString(R.string.home_region_mea)}"
        "GLO", "GLOBAL" -> "GLO ${context.getString(R.string.home_region_global)} 🌐"
        else -> {
            val flag = regionalFlag(normalized)
            val country = if (normalized in Locale.getISOCountries())
                Locale.Builder().setRegion(normalized).build().getDisplayCountry(locale) else ""
            listOf(normalized, country, flag.orEmpty()).filter(String::isNotBlank).joinToString(" ")
        }
    }
}

private fun regionalFlag(code: String): String? {
    if (code.length != 2 || code.any { it !in 'A'..'Z' }) return null
    val builder = StringBuilder()
    code.forEach { letter ->
        builder.appendCodePoint(0x1F1E6 + (letter - 'A'))
    }
    return builder.toString()
}

fun readHomeSystemProperty(key: String): String? {
    return runCatching {
        Class.forName("android.os.SystemProperties")
            .getMethod("get", String::class.java, String::class.java)
            .invoke(null, key, "") as? String
    }.getOrNull()?.takeIf(String::isNotBlank)
}

@Composable
private fun HomeInfoBasicRow(
    label: String,
    value: String,
) {
    BasicComponent {
        SettingsRowTextContent(
            title = label,
            summary = value,
        )
    }
}
