/*
 * Copyright 2026 wchill.
 *
 * Stable compile-only Boost adapter API. Native targets are in boost/bindings.json.
 * These declarations must never be packaged with the extension.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

@file:Suppress("UNUSED_PARAMETER", "unused", "RedundantVisibilityModifier")

package com.bumptech.glide

private fun <T> boostApiStub(): T = throw UnsupportedOperationException("Compile-only Boost adapter stub")

abstract class Glide protected constructor(stub: Unit) : android.content.ComponentCallbacks2 {
    companion object {
        @JvmStatic fun with(p0: android.content.Context?): com.bumptech.glide.RequestManager? = boostApiStub()
    }
}

abstract class RequestBuilder protected constructor(stub: Unit) : com.bumptech.glide.request.BaseRequestOptions(Unit) {
    fun addListener(p0: com.bumptech.glide.request.RequestListener?): RequestBuilder? = boostApiStub()
    fun into(p0: android.widget.ImageView?): Unit = boostApiStub()
}

abstract class RequestManager protected constructor(stub: Unit) : android.content.ComponentCallbacks2, com.bumptech.glide.manager.LifecycleListener {
    fun clear(p0: android.view.View?): Unit = boostApiStub()
    open fun load(p0: String?): com.bumptech.glide.RequestBuilder? = boostApiStub()
}
