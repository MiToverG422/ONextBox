package com.mi.onextbox.ui.settings

import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.platform.app.InstrumentationRegistry
import com.mi.onextbox.R
import com.mi.onextbox.ui.common.AppLocale
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalizedDeviceResourcesTest {
    @Test fun everyLanguageResolvesAndFormatsDeviceAndDiagnosticText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AppLocale.languages.forEach { language ->
            val configuration = Configuration(context.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(AppLocale.resourceLanguageTag(language.tag)))
            }
            val localized = context.createConfigurationContext(configuration)
            val label = localized.getString(R.string.device_battery_manufacture_date)
            assertTrue(language.tag, label.isNotBlank())
            if (language.tag != "en") assertNotEquals(language.tag, "Battery manufacture date", label)
            val updated = localized.getString(R.string.device_updated, "12:34:56")
            assertTrue(language.tag, "12:34:56" in updated)
            val luminance = localized.getString(R.string.device_hdr_luminance_values, "1000 nit", "500 nit", "0.01 nit")
            assertTrue(language.tag, luminance.lines().size == 3)
            assertFalse(language.tag, "\\n" in luminance)
            assertTrue(language.tag, localized.getString(R.string.diag_trace_remaining, 30L).isNotBlank())
            assertTrue(language.tag, localized.getString(R.string.diagnostics_permissions_count, 3, 5).isNotBlank())
            assertTrue(language.tag, localized.resources.getQuantityString(R.plurals.device_cycles_count, 1, 1).isNotBlank())
            assertTrue(language.tag, localized.resources.getQuantityString(R.plurals.device_cycles_count, 5, 5).isNotBlank())
            assertTrue(language.tag, localized.getString(R.string.diag_bundle_readme, "2026-11-17").lines().size >= 3)
        }
    }
}
