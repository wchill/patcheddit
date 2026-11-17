/*
 * Copyright 2026 wchill.
 *
 * Stable compile-only Boost adapter API. Native targets are in boost/bindings.json.
 * These declarations must never be packaged with the extension.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

@file:Suppress("UNUSED_PARAMETER", "unused", "RedundantVisibilityModifier")

package com.rubenmayayo.reddit.ui.adapters

private fun <T> boostApiStub(): T = throw UnsupportedOperationException("Compile-only Boost adapter stub")

abstract class CommentViewHolder protected constructor(stub: Unit) : androidx.recyclerview.widget.RecyclerView.ViewHolder(Unit) {
    @JvmField var menuDialog: com.afollestad.materialdialogs.MaterialDialog? = boostApiStub()
    fun bind(p0: com.rubenmayayo.reddit.models.reddit.CommentModel?, p1: Boolean, p2: Boolean, p3: Boolean, p4: com.bumptech.glide.RequestManager?): Unit = boostApiStub()
}

abstract class LoadMoreViewHolder protected constructor(stub: Unit) : androidx.recyclerview.widget.RecyclerView.ViewHolder(Unit), android.view.View.OnClickListener {
    interface Listener {
    }
}

abstract class SearchHeaderViewHolder protected constructor(stub: Unit) : androidx.recyclerview.widget.RecyclerView.ViewHolder(Unit) {
    interface Listener {
    }
}

abstract class SearchOptionViewHolder protected constructor(stub: Unit) : androidx.recyclerview.widget.RecyclerView.ViewHolder(Unit), android.view.View.OnClickListener, android.view.View.OnLongClickListener {
    interface Listener {
    }
}

abstract class SubmissionViewHolder protected constructor(stub: Unit) : androidx.recyclerview.widget.RecyclerView.ViewHolder(Unit) {
    fun bind(p0: com.rubenmayayo.reddit.models.reddit.SubmissionModel?, p1: Boolean, p2: Boolean, p3: Boolean, p4: com.bumptech.glide.RequestManager?): Unit = boostApiStub()
}
