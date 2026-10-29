package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class PictorialImageRevisionsTest {
    @Test fun handlesEveryRotationAndDeduplicatesRepeatedBitmapRequests() {
        val images = PictorialImageRevisions()
        assertEquals(PictorialImageRevisions.Request(true, true), images.request("a", true, true))
        assertEquals(PictorialImageRevisions.Request(false, false), images.request("a", true, true))
        assertEquals(PictorialImageRevisions.Request(true, true), images.request("b", true, true))
    }
    @Test fun enablingOneFeatureAfterAnotherStillProcessesTheCurrentImage() {
        val images = PictorialImageRevisions()
        images.request("a", false, false)
        assertEquals(PictorialImageRevisions.Request(false, true), images.request("a", false, true))
        assertEquals(PictorialImageRevisions.Request(true, false), images.request("a", true, true))
    }
    @Test fun emptyOrMissingImageDoesNotConsumeTheNextRealRotation() {
        val images = PictorialImageRevisions()
        assertEquals(PictorialImageRevisions.Request(false, false), images.request("", true, true))
        assertEquals(PictorialImageRevisions.Request(true, true), images.request("a", true, true))
    }
}
