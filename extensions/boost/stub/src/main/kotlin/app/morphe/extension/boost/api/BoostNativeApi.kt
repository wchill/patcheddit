/*
 * Copyright 2026 wchill.
 *
 * Stable compile-only Boost adapter API. Native targets are in boost/bindings.json.
 * These declarations must never be packaged with the extension.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

@file:Suppress("UNUSED_PARAMETER", "unused", "RedundantVisibilityModifier")

package app.morphe.extension.boost.api

private fun <T> boostApiStub(): T = throw UnsupportedOperationException("Compile-only Boost adapter stub")

abstract class ActionBarDrawerToggle protected constructor(stub: Unit) : androidx.drawerlayout.widget.DrawerLayout.DrawerListener {
}

abstract class AccountStore protected constructor(stub: Unit) {
    companion object {
        @JvmStatic fun users(): List<com.rubenmayayo.reddit.aa.User> = boostApiStub()
    }
}

abstract class BackActivity protected constructor(stub: Unit) : com.rubenmayayo.reddit.ui.activities.BaseActivity(Unit) {
}

abstract class BaseImageActivity protected constructor(stub: Unit) : app.morphe.extension.boost.api.DownloadMediaActivity(Unit) {
}

abstract class BoostPreferences protected constructor(stub: Unit) {
    @JvmField var preferences: android.content.SharedPreferences = boostApiStub()
    fun defaultPeriod(): net.dean.jraw.paginators.TimePeriod? = boostApiStub()
    fun defaultSorting(): net.dean.jraw.paginators.Sorting? = boostApiStub()
    fun includeNsfw(): Boolean = boostApiStub()
    fun leftHanded(): Boolean = boostApiStub()
    fun legacyDownloadFolder(p0: Int): String? = boostApiStub()
    fun safDownloadFolder(p0: Int): String? = boostApiStub()
    fun loadFilters(p0: String?): java.util.ArrayList<*>? = boostApiStub()
    fun resetLegacyDownloadFolder(p0: Int): Unit = boostApiStub()
    fun resetSafDownloadFolder(p0: Int): Unit = boostApiStub()
    fun fullPreview(): Boolean = boostApiStub()
    fun getApiPreference(p0: String): String? = boostApiStub()
    fun getUserAgent(): String = boostApiStub()
    fun roundedCorners(): Boolean = boostApiStub()
    fun saveFilters(p0: String?, p1: List<String>?): Unit = boostApiStub()
    fun setColorPattern(p0: IntArray?): Unit = boostApiStub()
    fun saveDefaultSort(p0: String?): Unit = boostApiStub()
    companion object {
        @JvmStatic fun resolveFont(p0: android.content.Context?, p1: String?): android.graphics.Typeface? = boostApiStub()
        @JvmStatic fun downloadFolderKey(p0: Int): String? = boostApiStub()
        @JvmStatic fun getInstance(): app.morphe.extension.boost.api.BoostPreferences? = boostApiStub()
        @JvmField var subredditPrefix: String = boostApiStub()
        @JvmField var layoutDirty: Boolean = boostApiStub()
        @JvmField var toolbarDirty: Boolean = boostApiStub()
        @JvmField var postViewsDirty: Boolean = boostApiStub()
        @JvmField var postTypographyDirty: Boolean = boostApiStub()
        @JvmField var themeDirty: Boolean = boostApiStub()
        @JvmField var commentTypographyDirty: Boolean = boostApiStub()
    }
}

abstract class BottomSheetMenu protected constructor(stub: Unit) : com.google.android.material.bottomsheet.BottomSheetDialogFragment(Unit) {
    interface OnMenuOptionSelected {
    }
}

abstract class BottomSheetPro protected constructor(stub: Unit) : com.google.android.material.bottomsheet.BottomSheetDialogFragment(Unit) {
    interface ProResultListener {
    }
}

abstract class CacheUtils protected constructor(stub: Unit) {
    companion object {
        @JvmStatic fun clear(p0: android.content.Context?): Unit = boostApiStub()
        @JvmStatic fun size(p0: android.content.Context?): String? = boostApiStub()
    }
}

interface CaptchaView : app.morphe.extension.boost.api.MvpLoadingView {
}

abstract class ChannelFlow protected constructor(stub: Unit) : app.morphe.extension.boost.api.internal.MeasurementProvider {
}

interface CommentViewHolderListener {
}

interface CommentsView : app.morphe.extension.boost.api.ReplyView {
}

interface ContributionView : app.morphe.extension.boost.api.ThingView {
}

open class DefaultExtractorsFactory protected constructor(stub: Unit) {
    constructor() : this(Unit) { boostApiStub<Unit>() }
}

abstract class DiskCacheAdapter protected constructor(stub: Unit) : com.bumptech.glide.load.engine.cache.DiskCache, app.morphe.extension.boost.api.HeartBeatConsumer, app.morphe.extension.boost.api.internal.MeasurementProvider, app.morphe.extension.boost.api.internal.AdsIdentifierService {
    companion object {
        @JvmStatic fun resizeMediaCache(p0: android.content.ContextWrapper?): Unit = boostApiStub()
    }
}

abstract class DoneUtils protected constructor(stub: Unit) {
    fun resetTips(): Unit = boostApiStub()
    companion object {
        @JvmStatic fun getInstance(): app.morphe.extension.boost.api.DoneUtils? = boostApiStub()
    }
}

abstract class DownloadMediaActivity protected constructor(stub: Unit) : com.rubenmayayo.reddit.ui.activities.BaseActivity(Unit) {
}

abstract class Drawer protected constructor(stub: Unit) {
    interface ItemListener {
    }
    interface SelectionListener {
    }
}

abstract class DrawerActivity protected constructor(stub: Unit) : com.rubenmayayo.reddit.ui.activities.ToolbarActivity(Unit), app.morphe.extension.boost.api.Drawer.ItemListener, app.morphe.extension.boost.api.Drawer.SelectionListener, com.rubenmayayo.reddit.ui.customviews.dialogs.UserSelectorView.Callback {
    open fun onNavigationLongPress(p0: Int, p1: Boolean): Unit = boostApiStub()
    fun selectAccount(p0: Int): Unit = boostApiStub()
    open fun currentCommunity(): String? = boostApiStub()
}

abstract class EffectiveFragment protected constructor(stub: Unit) : androidx.fragment.app.Fragment(Unit) {
}

abstract class EntityDeletionOrUpdateAdapter protected constructor(stub: Unit) : app.morphe.extension.boost.api.runtime.androidx.room.SharedSQLiteStatement(Unit) {
    fun execute(p0: Any?): Unit = boostApiStub()
}

abstract class EntityInsertionAdapter protected constructor(stub: Unit) : app.morphe.extension.boost.api.runtime.androidx.room.SharedSQLiteStatement(Unit) {
    fun insert(p0: Any?): Unit = boostApiStub()
}

open class ExoPlayerBuilder protected constructor(stub: Unit) {
    constructor(p0: android.content.Context?) : this(Unit) { boostApiStub<Unit>() }
    fun build(): com.google.android.exoplayer2.ExoPlayerImpl? = boostApiStub()
}

interface HeartBeatConsumer {
}

abstract class HttpClient protected constructor(stub: Unit) {
    companion object {
        @JvmField var client: okhttp3.OkHttpClient = boostApiStub()
    }
}

abstract class InjectorUtils protected constructor(stub: Unit) {
    companion object {
        @JvmStatic fun savedSearchViewModelFactory(p0: android.content.Context?): app.morphe.extension.boost.api.SearchViewModelFactory? = boostApiStub()
    }
}

abstract class LRUCache protected constructor(stub: Unit) : java.util.LinkedHashMap<Any?, Any?>() {
}

abstract class MediaBaseActivity protected constructor(stub: Unit) : com.rubenmayayo.reddit.ui.activities.BaseActivity(Unit) {
}

abstract class MediaUploadFactory protected constructor(stub: Unit) : app.morphe.extension.boost.api.internal.MeasurementProvider, app.morphe.extension.boost.api.internal.BillingCallback {
    companion object {
        @JvmStatic fun create(p0: String?): app.morphe.extension.boost.api.MediaUploader? = boostApiStub()
    }
}

interface MediaUploader {
    fun uploadBatch(p0: List<*>?, p1: app.morphe.extension.boost.api.ProgressRequestBody.ProgressListener?, p2: app.morphe.extension.boost.api.MediaUploaderListener?): Unit
    fun uploadGifv(p0: com.rubenmayayo.reddit.models.imgur.Upload?, p1: com.rubenmayayo.reddit.ui.submit.v2.SubmitGifvFragment.ProgressListener?, p2: com.rubenmayayo.reddit.ui.submit.v2.SubmitGifvFragment.UploadListener?): Unit
    fun upload(p0: com.rubenmayayo.reddit.models.imgur.Upload?, p1: app.morphe.extension.boost.api.ProgressRequestBody.ProgressListener?, p2: app.morphe.extension.boost.api.MediaUploaderListener?): Unit
}

interface MediaUploaderListener {
    fun onFailure(p0: String?): Unit
    fun onSuccess(p0: String?): Unit
    fun onImageUploaded(p0: com.rubenmayayo.reddit.models.imgur.ImageResponse.UploadedImage?): Unit
    fun onGalleryUploaded(p0: List<net.dean.jraw.managers.AccountManager.GalleryItem>?): Unit
}

interface MessageViewHolderListener {
}

interface MessagesView : app.morphe.extension.boost.api.ReplyView {
}

interface MvpLoadingView : app.morphe.extension.boost.api.MvpViewMo {
}

interface MvpViewMo {
}

abstract class MyRedditClient protected constructor(stub: Unit) : net.dean.jraw.RedditClient(boostApiStub<net.dean.jraw.http.UserAgent>()) {
}

abstract class Navigation protected constructor(stub: Unit) {
    companion object {
        @JvmStatic fun openUser(p0: android.content.Context?, p1: String?): Unit = boostApiStub()
        @JvmStatic fun openCommunity(p0: android.app.Activity?, p1: com.rubenmayayo.reddit.models.reddit.SubscriptionViewModel?): Unit = boostApiStub()
        @JvmStatic fun openSupport(p0: android.content.Context?): Unit = boostApiStub()
        @JvmStatic fun openWiki(p0: android.app.Activity?, p1: String?, p2: String?): Unit = boostApiStub()
        @JvmStatic fun rateApp(p0: android.app.Activity?): Unit = boostApiStub()
        @JvmStatic fun imageIntent(p0: android.content.Context?, p1: com.rubenmayayo.reddit.models.reddit.SubmissionModel?, p2: Boolean): android.content.Intent? = boostApiStub()
        @JvmStatic fun openAnimatedMedia(p0: android.content.Context?, p1: com.rubenmayayo.reddit.models.reddit.SubmissionModel?, p2: Boolean): Unit = boostApiStub()
        @JvmStatic fun openImage(p0: android.content.Context?, p1: com.rubenmayayo.reddit.models.reddit.SubmissionModel?, p2: Boolean): Unit = boostApiStub()
        @JvmStatic fun addAccount(p0: android.app.Activity?): Unit = boostApiStub()
    }
}

abstract class NativeJsonModel protected constructor(stub: Unit) :
    net.dean.jraw.models.JsonModel(boostApiStub<com.fasterxml.jackson.databind.JsonNode>()) {
    fun dataNode(): app.morphe.extension.boost.api.NativeJsonNode = boostApiStub()
}

abstract class NativeJsonNode protected constructor(stub: Unit) {
    fun get(p0: String): app.morphe.extension.boost.api.NativeJsonNode? = boostApiStub()
    fun asBoolean(): Boolean = boostApiStub()
}

interface NestedScrollingChild {
}

abstract class NightUtils protected constructor(stub: Unit) {
    companion object {
        @JvmStatic fun updateThemeMode(): Unit = boostApiStub()
    }
}

abstract class PostHistoryUtils protected constructor(stub: Unit) {
    fun clear(): Unit = boostApiStub()
    companion object {
        @JvmStatic fun getInstance(): app.morphe.extension.boost.api.PostHistoryUtils? = boostApiStub()
    }
}

abstract class ProgressRequestBody protected constructor(stub: Unit) : okhttp3.RequestBody() {
    interface ProgressListener {
        fun onProgress(p0: Int): Unit
    }
}

abstract class ReadHistory protected constructor(stub: Unit) {
    @JvmField var preferences: android.content.SharedPreferences = boostApiStub()
    companion object {
        @JvmStatic fun getInstance(): app.morphe.extension.boost.api.ReadHistory? = boostApiStub()
    }
}

abstract class RedditAccount protected constructor(stub: Unit) {
    @JvmField var user: com.rubenmayayo.reddit.aa.User? = boostApiStub()
    fun username(): String? = boostApiStub()
}

abstract class RedditJraw protected constructor(stub: Unit) : app.morphe.extension.boost.api.RedditAccount(Unit) {
    @JvmField var client: app.morphe.extension.boost.api.MyRedditClient = boostApiStub()
    @JvmField var credentials: net.dean.jraw.http.oauth.Credentials? = boostApiStub()
    fun selectAccount(p0: Int): Unit = boostApiStub()
    fun trendingSearches(): List<*>? = boostApiStub()
    fun trendingRows(): List<*>? = boostApiStub()
    companion object {
        @JvmStatic fun initialize(): Unit = boostApiStub()
        @JvmField var instance: app.morphe.extension.boost.api.RedditJraw = boostApiStub()
    }
}

abstract class Reflection protected constructor(stub: Unit) {
    companion object {
        @JvmStatic fun getKotlinClass(p0: Class<*>?): app.morphe.extension.boost.api.runtime.kotlin.jvm.internal.ClassReference? = boostApiStub()
    }
}

abstract class RemoteConfigHelper protected constructor(stub: Unit) {
    companion object {
        @JvmStatic fun isEnabled(p0: String?): Boolean = boostApiStub()
    }
}

interface ReplyView : app.morphe.extension.boost.api.MvpLoadingView {
}

abstract class RoomTrackingLiveData protected constructor(stub: Unit) : androidx.lifecycle.LiveData(Unit) {
}

open class SavedSearch protected constructor(stub: Unit) {
    constructor() : this(Unit) { boostApiStub<Unit>() }
    @JvmField var id: Int = boostApiStub()
    @JvmField var name: String? = boostApiStub()
    @JvmField var community: String? = boostApiStub()
    @JvmField var scopeId: String? = boostApiStub()
    @JvmField var query: String? = boostApiStub()
    @JvmField var sort: String? = boostApiStub()
    @JvmField var period: String? = boostApiStub()
}

abstract class SavedSearchDao protected constructor(stub: Unit) {
    @JvmField val database: app.morphe.extension.boost.api.runtime.androidx.room.RoomDatabase = boostApiStub()
    @JvmField val insertAdapter: app.morphe.extension.boost.api.SavedSearchInsertAdapter = boostApiStub()
    @JvmField val deleteAdapter: app.morphe.extension.boost.api.SavedSearchDeleteAdapter = boostApiStub()
    @JvmField val updateAdapter: app.morphe.extension.boost.api.SavedSearchUpdateAdapter = boostApiStub()
}

abstract class SavedSearchDeleteAdapter protected constructor(stub: Unit) : app.morphe.extension.boost.api.EntityDeletionOrUpdateAdapter(Unit) {
}

abstract class SavedSearchInsertAdapter protected constructor(stub: Unit) : app.morphe.extension.boost.api.EntityInsertionAdapter(Unit) {
}

abstract class SavedSearchUpdateAdapter protected constructor(stub: Unit) : app.morphe.extension.boost.api.EntityDeletionOrUpdateAdapter(Unit) {
}

abstract class SavedSearchViewModel protected constructor(stub: Unit) : androidx.lifecycle.ViewModel(Unit) {
    @JvmField var repository: app.morphe.extension.boost.api.SearchRepository = boostApiStub()
    @JvmField var searches: app.morphe.extension.boost.api.RoomTrackingLiveData? = boostApiStub()
}

abstract class SearchCache protected constructor(stub: Unit) {
    @JvmField var cache: app.morphe.extension.boost.api.LRUCache? = boostApiStub()
    companion object {
        @JvmStatic fun getInstance(): app.morphe.extension.boost.api.SearchCache? = boostApiStub()
    }
}

open class SearchOption protected constructor(stub: Unit) {
    constructor(p0: Int, p1: String?) : this(Unit) { boostApiStub<Unit>() }
    @JvmField var subscription: com.rubenmayayo.reddit.models.reddit.SubscriptionViewModel = boostApiStub()
    @JvmField var subreddit: com.rubenmayayo.reddit.models.reddit.SubredditModel = boostApiStub()
}

abstract class SearchRepository protected constructor(stub: Unit) {
    @JvmField var dao: app.morphe.extension.boost.api.SavedSearchDao = boostApiStub()
}

interface SearchSubmissionsView : app.morphe.extension.boost.api.SubmissionsView {
}

abstract class SearchViewModelFactory protected constructor(stub: Unit) : androidx.lifecycle.ViewModelProvider.Factory {
}

abstract class SortMenu protected constructor(stub: Unit) : app.morphe.extension.boost.api.internal.MeasurementProvider {
    companion object {
        @JvmStatic fun sortOptions(p0: com.rubenmayayo.reddit.models.reddit.SubscriptionViewModel?, p1: Boolean, p2: Boolean): java.util.ArrayList<*>? = boostApiStub()
    }
}

abstract class SortSelection protected constructor(stub: Unit) {
}

abstract class SortUtils protected constructor(stub: Unit) {
    @JvmField var preferences: android.content.SharedPreferences = boostApiStub()
    fun clearSubscriptionSort(p0: com.rubenmayayo.reddit.models.reddit.SubscriptionViewModel?): Unit = boostApiStub()
    fun saveSubscriptionSort(p0: Int, p1: String?): Unit = boostApiStub()
    companion object {
        @JvmStatic fun getInstance(): app.morphe.extension.boost.api.SortUtils? = boostApiStub()
        @JvmStatic fun subscriptionKey(p0: com.rubenmayayo.reddit.models.reddit.SubscriptionViewModel?): String? = boostApiStub()
        @JvmStatic fun resolveSort(p0: Int): app.morphe.extension.boost.api.SortSelection? = boostApiStub()
    }
}

interface SpecialEffectsControllerFactory {
}

interface SubmissionViewHolderListener {
}

interface SubmissionsView : app.morphe.extension.boost.api.ThingView {
}

interface SubmitView : app.morphe.extension.boost.api.CaptchaView {
}

abstract class SubredditHistoryUtils protected constructor(stub: Unit) {
    @JvmField var preferences: android.content.SharedPreferences = boostApiStub()
    companion object {
        @JvmStatic fun getInstance(): app.morphe.extension.boost.api.SubredditHistoryUtils? = boostApiStub()
    }
}

interface SubredditView : app.morphe.extension.boost.api.SubmissionsView {
}

interface SubscriptionsView : app.morphe.extension.boost.api.MvpLoadingView {
}

abstract class ThemeUtils protected constructor(stub: Unit) {
    companion object {
        @JvmStatic fun accentColor(p0: android.content.Context?): Int = boostApiStub()
        @JvmStatic fun themeIndex(p0: Int): Int = boostApiStub()
        @JvmField val themeTitles: IntArray = boostApiStub()
    }
}

interface ThingView : app.morphe.extension.boost.api.MvpLoadingView {
}

open class TrendingSearch protected constructor(stub: Unit) {
    constructor() : this(Unit) { boostApiStub<Unit>() }
    @JvmField var title: String = boostApiStub()
    @JvmField var submissions: List<com.rubenmayayo.reddit.models.reddit.SubmissionModel>? = boostApiStub()
}

abstract class TrendingTodayViewModel protected constructor(stub: Unit) : androidx.lifecycle.ViewModel(Unit) {
}

abstract class Utils protected constructor(stub: Unit) {
    companion object {
        @JvmStatic fun subscriptionsChanged(p0: android.content.Context?): Unit = boostApiStub()
        @JvmStatic fun subscriptionName(p0: android.content.Context?, p1: com.rubenmayayo.reddit.models.reddit.SubscriptionViewModel?): String? = boostApiStub()
    }
}

open class ViewModelProviderImpl protected constructor(stub: Unit) {
    constructor(p0: androidx.lifecycle.ViewModelStore?, p1: androidx.lifecycle.ViewModelProvider.Factory?, p2: androidx.lifecycle.viewmodel.CreationExtras?) : this(Unit) { boostApiStub<Unit>() }
    fun getViewModel(p0: app.morphe.extension.boost.api.runtime.kotlin.jvm.internal.ClassReference?, p1: String?): androidx.lifecycle.ViewModel? = boostApiStub()
}

abstract class ViewUtils protected constructor(stub: Unit) {
    @JvmField var preferences: android.content.SharedPreferences = boostApiStub()
    companion object {
        @JvmStatic fun getInstance(): app.morphe.extension.boost.api.ViewUtils? = boostApiStub()
    }
}
