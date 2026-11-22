/*
 * Copyright 2026 wchill.
 *
 * Stable compile-only Boost adapter API. Native targets are in boost/bindings.json.
 * These declarations must never be packaged with the extension.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

@file:Suppress("UNUSED_PARAMETER", "unused", "RedundantVisibilityModifier")

package com.bumptech.glide.request

private fun <T> boostApiStub(): T = throw UnsupportedOperationException("Compile-only Boost adapter stub")

abstract class BaseRequestOptions protected constructor(stub: Unit) : Cloneable {
    fun override(width: Int, height: Int): BaseRequestOptions = boostApiStub()
}

// Boost's R8-specialized listener does not retain Glide's upstream signatures.
interface RequestListener {
    fun onResourceReady(resource: Any?)
    fun onLoadFailed(exception: com.bumptech.glide.load.engine.GlideException?): Boolean
}
