package com.mi.onextbox.lsp

internal class PictorialImageRevisions {
    data class Request(val home: Boolean, val clock: Boolean)
    private var homeImage: String? = null
    private var clockImage: String? = null

    @Synchronized fun request(id: String, home: Boolean, clock: Boolean): Request {
        if (id.isBlank()) return Request(false, false)
        val result = Request(home && id != homeImage, clock && id != clockImage)
        if (result.home) homeImage = id
        if (result.clock) clockImage = id
        return result
    }
}
