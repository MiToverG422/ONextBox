package com.mi.onextbox

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.assertEquals

/** Verifies the installed application ID. */
@RunWith(AndroidJUnit4::class)
class ApplicationIdTest {
    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.mi.onextbox", appContext.packageName)
    }
}
