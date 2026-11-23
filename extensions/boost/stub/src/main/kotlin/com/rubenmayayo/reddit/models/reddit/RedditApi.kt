/*
 * Copyright 2026 wchill.
 *
 * Stable compile-only Boost adapter API. Native targets are in boost/bindings.json.
 * These declarations must never be packaged with the extension.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

@file:Suppress("UNUSED_PARAMETER", "unused", "RedundantVisibilityModifier")

package com.rubenmayayo.reddit.models.reddit

private fun <T> boostApiStub(): T = throw UnsupportedOperationException("Compile-only Boost adapter stub")

abstract class CommentModel protected constructor(stub: Unit) : com.rubenmayayo.reddit.models.reddit.CommentViewModel(Unit) {
    @JvmField var body: String = boostApiStub()
    @JvmField var bodyHtml: String = boostApiStub()
    companion object {
        @JvmStatic fun commentModelDeserialize(p0: net.dean.jraw.models.Comment?): com.rubenmayayo.reddit.models.reddit.CommentModel? = boostApiStub()
    }
}

abstract class CommentViewModel protected constructor(stub: Unit) : com.rubenmayayo.reddit.models.reddit.PublicContributionModel(Unit) {
}

abstract class ContributionModel protected constructor(stub: Unit) : com.rubenmayayo.reddit.models.reddit.ThingModel(Unit) {
}

abstract class PublicContributionModel protected constructor(stub: Unit) : com.rubenmayayo.reddit.models.reddit.ContributionModel(Unit) {
}

open class SubmissionModel protected constructor(stub: Unit) : com.rubenmayayo.reddit.models.reddit.PublicContributionModel(Unit), java.io.Serializable {
    constructor() : this(Unit) { boostApiStub<Unit>() }
    @JvmField var mediaType: Int = boostApiStub()
    @JvmField var videoUrl: String = boostApiStub()
    @JvmField var domain: String = boostApiStub()
    @JvmField var mediaUrl: String = boostApiStub()
    override fun describeContents(): Int = boostApiStub()
    override fun writeToParcel(p0: android.os.Parcel, p1: Int): Unit = boostApiStub()
    companion object {
        @JvmStatic fun fromSubmission(p0: net.dean.jraw.models.Submission?): com.rubenmayayo.reddit.models.reddit.SubmissionModel? = boostApiStub()
    }
}

open class SubredditModel protected constructor(stub: Unit) : com.rubenmayayo.reddit.models.reddit.SubscriptionModel(Unit), android.os.Parcelable, Comparable<Any?> {
    constructor(p0: String?) : this(Unit) { boostApiStub<Unit>() }
    constructor(p0: net.dean.jraw.models.Subreddit?) : this(Unit) { boostApiStub<Unit>() }
    @JvmField var description: String = boostApiStub()
    @JvmField var publicDescription: String = boostApiStub()
    @JvmField var subscribers: Long = boostApiStub()
    override fun compareTo(p0: Any?): Int = boostApiStub()
    override fun describeContents(): Int = boostApiStub()
    override fun writeToParcel(p0: android.os.Parcel, p1: Int): Unit = boostApiStub()
}

abstract class SubscriptionModel protected constructor(stub: Unit) : com.rubenmayayo.reddit.models.reddit.ThingModel(Unit), android.os.Parcelable {
    @JvmField var iconUrl: String = boostApiStub()
    @JvmField var themeColor: String = boostApiStub()
}

open class SubscriptionViewModel protected constructor(stub: Unit) : android.os.Parcelable {
    constructor(p0: com.rubenmayayo.reddit.models.reddit.SubredditModel?) : this(Unit) { boostApiStub<Unit>() }
    constructor(p0: String?) : this(Unit) { boostApiStub<Unit>() }
    constructor(p0: String?, p1: Boolean) : this(Unit) { boostApiStub<Unit>() }
    @JvmField var community: String = boostApiStub()
    @JvmField var isMultireddit: Boolean = boostApiStub()
    override fun describeContents(): Int = boostApiStub()
    override fun writeToParcel(p0: android.os.Parcel, p1: Int): Unit = boostApiStub()
    companion object {
        @JvmStatic fun frontpage(): com.rubenmayayo.reddit.models.reddit.SubscriptionViewModel? = boostApiStub()
    }
}

abstract class ThingModel protected constructor(stub: Unit) : android.os.Parcelable {
}
