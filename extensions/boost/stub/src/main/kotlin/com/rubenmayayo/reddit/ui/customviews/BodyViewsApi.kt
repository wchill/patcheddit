/*
 * Copyright 2026 wchill.
 *
 * Compile-only Boost body bindings.
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

@file:Suppress("UNUSED_PARAMETER", "unused")

package com.rubenmayayo.reddit.ui.customviews

private fun <T> stub(): T = throw UnsupportedOperationException("Compile-only Boost adapter stub")

abstract class LinkTextView protected constructor() : android.widget.TextView(stub<android.content.Context>())

abstract class TableTextView protected constructor() : android.widget.LinearLayout(stub<android.content.Context>()) {
    fun createTextView(): LinkTextView? = stub()
}
