/*
 * Copyright 2026 wchill.
 *
 * Java emits native static calls directly instead of Kotlin companion calls.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

public final class MorpheBoostNativeStatics {
    private MorpheBoostNativeStatics() {}

    public static androidx.fragment.app.Fragment Fragment_instantiate(android.content.Context p0, java.lang.String p1) { return androidx.fragment.app.Fragment.instantiate(p0, p1); }
    public static androidx.fragment.app.Fragment Fragment_instantiate(android.content.Context p0, java.lang.String p1, android.os.Bundle p2) { return androidx.fragment.app.Fragment.instantiate(p0, p1, p2); }
    public static java.lang.String BoostPreferences_downloadFolderKey(int p0) { return app.morphe.extension.boost.api.BoostPreferences.downloadFolderKey(p0); }
    public static app.morphe.extension.boost.api.BoostPreferences BoostPreferences_getInstance() { return app.morphe.extension.boost.api.BoostPreferences.getInstance(); }
    public static android.graphics.Typeface BoostPreferences_resolveFont(android.content.Context p0, java.lang.String p1) { return app.morphe.extension.boost.api.BoostPreferences.resolveFont(p0, p1); }
    public static void CacheUtils_clear(android.content.Context p0) { app.morphe.extension.boost.api.CacheUtils.clear(p0); }
    public static java.lang.String CacheUtils_size(android.content.Context p0) { return app.morphe.extension.boost.api.CacheUtils.size(p0); }
    public static void DiskCacheAdapter_resizeMediaCache(android.content.ContextWrapper p0) { app.morphe.extension.boost.api.DiskCacheAdapter.resizeMediaCache(p0); }
    public static app.morphe.extension.boost.api.DoneUtils DoneUtils_getInstance() { return app.morphe.extension.boost.api.DoneUtils.getInstance(); }
    public static app.morphe.extension.boost.api.SearchViewModelFactory InjectorUtils_savedSearchViewModelFactory(android.content.Context p0) { return app.morphe.extension.boost.api.InjectorUtils.savedSearchViewModelFactory(p0); }
    public static app.morphe.extension.boost.api.MediaUploader MediaUploadFactory_create(java.lang.String p0) { return app.morphe.extension.boost.api.MediaUploadFactory.create(p0); }
    public static void Navigation_addAccount(android.app.Activity p0) { app.morphe.extension.boost.api.Navigation.addAccount(p0); }
    public static android.content.Intent Navigation_imageIntent(android.content.Context p0, com.rubenmayayo.reddit.models.reddit.SubmissionModel p1, boolean p2) { return app.morphe.extension.boost.api.Navigation.imageIntent(p0, p1, p2); }
    public static void Navigation_openAnimatedMedia(android.content.Context p0, com.rubenmayayo.reddit.models.reddit.SubmissionModel p1, boolean p2) { app.morphe.extension.boost.api.Navigation.openAnimatedMedia(p0, p1, p2); }
    public static void Navigation_openCommunity(android.app.Activity p0, java.lang.String p1) { app.morphe.extension.boost.api.Navigation.openCommunity(p0, new com.rubenmayayo.reddit.models.reddit.SubscriptionViewModel(p1)); }
    public static void Navigation_openImage(android.content.Context p0, com.rubenmayayo.reddit.models.reddit.SubmissionModel p1, boolean p2) { app.morphe.extension.boost.api.Navigation.openImage(p0, p1, p2); }
    public static void Navigation_openSupport(android.content.Context p0) { app.morphe.extension.boost.api.Navigation.openSupport(p0); }
    public static void Navigation_openUser(android.content.Context p0, java.lang.String p1) { app.morphe.extension.boost.api.Navigation.openUser(p0, p1); }
    public static void Navigation_openWiki(android.app.Activity p0, java.lang.String p1, java.lang.String p2) { app.morphe.extension.boost.api.Navigation.openWiki(p0, p1, p2); }
    public static void Navigation_rateApp(android.app.Activity p0) { app.morphe.extension.boost.api.Navigation.rateApp(p0); }
    public static void NightUtils_updateThemeMode() { app.morphe.extension.boost.api.NightUtils.updateThemeMode(); }
    public static app.morphe.extension.boost.api.PostHistoryUtils PostHistoryUtils_getInstance() { return app.morphe.extension.boost.api.PostHistoryUtils.getInstance(); }
    public static app.morphe.extension.boost.api.ReadHistory ReadHistory_getInstance() { return app.morphe.extension.boost.api.ReadHistory.getInstance(); }
    public static app.morphe.extension.boost.api.runtime.kotlin.jvm.internal.ClassReference Reflection_getKotlinClass(java.lang.Class p0) { return app.morphe.extension.boost.api.Reflection.getKotlinClass(p0); }
    public static boolean RemoteConfigHelper_isEnabled(java.lang.String p0) { return app.morphe.extension.boost.api.RemoteConfigHelper.isEnabled(p0); }
    public static app.morphe.extension.boost.api.SearchCache SearchCache_getInstance() { return app.morphe.extension.boost.api.SearchCache.getInstance(); }
    public static java.util.ArrayList SortMenu_sortOptions(com.rubenmayayo.reddit.models.reddit.SubscriptionViewModel p0, boolean p1, boolean p2) { return app.morphe.extension.boost.api.SortMenu.sortOptions(p0, p1, p2); }
    public static app.morphe.extension.boost.api.SortUtils SortUtils_getInstance() { return app.morphe.extension.boost.api.SortUtils.getInstance(); }
    public static app.morphe.extension.boost.api.SortSelection SortUtils_resolveSort(int p0) { return app.morphe.extension.boost.api.SortUtils.resolveSort(p0); }
    public static java.lang.String SortUtils_subscriptionKey(com.rubenmayayo.reddit.models.reddit.SubscriptionViewModel p0) { return app.morphe.extension.boost.api.SortUtils.subscriptionKey(p0); }
    public static app.morphe.extension.boost.api.SubredditHistoryUtils SubredditHistoryUtils_getInstance() { return app.morphe.extension.boost.api.SubredditHistoryUtils.getInstance(); }
    public static int ThemeUtils_accentColor(android.content.Context p0) { return app.morphe.extension.boost.api.ThemeUtils.accentColor(p0); }
    public static int ThemeUtils_themeIndex(int p0) { return app.morphe.extension.boost.api.ThemeUtils.themeIndex(p0); }
    public static java.lang.String Utils_subscriptionName(android.content.Context p0, com.rubenmayayo.reddit.models.reddit.SubscriptionViewModel p1) { return app.morphe.extension.boost.api.Utils.subscriptionName(p0, p1); }
    public static void Utils_subscriptionsChanged(android.content.Context p0) { app.morphe.extension.boost.api.Utils.subscriptionsChanged(p0); }
    public static app.morphe.extension.boost.api.ViewUtils ViewUtils_getInstance() { return app.morphe.extension.boost.api.ViewUtils.getInstance(); }
    public static com.bumptech.glide.RequestManager Glide_with(android.content.Context p0) { return com.bumptech.glide.Glide.with(p0); }
    public static com.google.android.exoplayer2.MediaItem MediaItem_fromUri(android.net.Uri p0) { return com.google.android.exoplayer2.MediaItem.fromUri(p0); }
    public static java.lang.String Snudown_markdown(java.lang.String p0) { return com.rubenmayayo.androidsnudown.Snudown.markdown(p0); }
    public static com.rubenmayayo.reddit.models.reddit.SubmissionModel SubmissionModel_fromSubmission(net.dean.jraw.models.Submission p0) { return com.rubenmayayo.reddit.models.reddit.SubmissionModel.fromSubmission(p0); }
    public static com.rubenmayayo.reddit.models.reddit.SubscriptionViewModel SubscriptionViewModel_frontpage() { return com.rubenmayayo.reddit.models.reddit.SubscriptionViewModel.frontpage(); }
    public static void CheckNotificationsWorker_disable(android.app.Activity p0) { com.rubenmayayo.reddit.work.notifications.CheckNotificationsWorker.disable(p0); }
    public static void CheckNotificationsWorker_enable(android.app.Activity p0) { com.rubenmayayo.reddit.work.notifications.CheckNotificationsWorker.enable(p0); }
    public static void TrendingWorker_schedule(android.content.Context p0, int p1) { com.rubenmayayo.reddit.work.trending.TrendingWorker.schedule(p0, p1); }
}
