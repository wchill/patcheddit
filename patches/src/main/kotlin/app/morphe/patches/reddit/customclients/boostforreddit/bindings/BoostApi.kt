/*
 * Copyright 2026 wchill.
 *
 * Stable names shared with the compile-only adapter API.
 * Native descriptors belong only in boost/bindings.json.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.bindings

internal object BoostApi {
    object AHBottomNavigationBehavior : BoostTypeBinding("Lcom/aurelhubert/ahbottomnavigation/AHBottomNavigationBehavior;") {
        val legacyNavigationSnackbarMargin get() = method("legacyNavigationSnackbarMargin(Landroid/view/View;Landroid/view/View;)Z")
    }
    object BabushkaText : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText;") {
        val addPart get() = method("addPart(Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText\$Part;)V")
        val parts get() = field("parts:Ljava/util/ArrayList;")
        val updateText get() = method("updateText()V")
    }
    object BabushkaTextPart : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText\$Part;") {
        val construct get() = method("<init>(Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText\$Part\$Builder;)V")
    }
    object BabushkaTextPartBuilder : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText\$Part\$Builder;") {
        val construct get() = method("<init>(Ljava/lang/String;)V")
    }
    object BaseActivity : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/activities/BaseActivity;") {
        val onCreate get() = method("onCreate(Landroid/os/Bundle;)V")
    }
    object BaseTransientBottomBar : BoostTypeBinding("Lcom/google/android/material/snackbar/BaseTransientBottomBar;") {
        val getAnchorView get() = method("getAnchorView()Landroid/view/View;")
        val setAnchorView get() = method("setAnchorView(Landroid/view/View;)V")
        val targetParent get() = field("targetParent:Landroid/view/ViewGroup;")
        val view get() = field("view:Lcom/google/android/material/snackbar/BaseTransientBottomBar\$SnackbarBaseLayout;")
    }
    object BaseTransientBottomBarSnackbarBaseLayout : BoostTypeBinding("Lcom/google/android/material/snackbar/BaseTransientBottomBar\$SnackbarBaseLayout;") {
    }
    object BoostMenuView : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/customviews/menu/MenuView;") {
        val setMenuOptions get() = method("setMenuOptions(Ljava/util/List;)V")
    }
    object BoostPreferences : BoostTypeBinding("Lapp/morphe/extension/boost/api/BoostPreferences;") {
        val getApiPreference get() = method("getApiPreference(Ljava/lang/String;)Ljava/lang/String;")
    }
    object CodecSpecificDataUtil : BoostTypeBinding("Lapp/morphe/extension/boost/api/CodecSpecificDataUtil;") {
        val findSubmissionIndex get() = method("findSubmissionIndex(Ljava/util/List;Lcom/rubenmayayo/reddit/models/reddit/SubmissionModel;)I")
    }
    object Comment : BoostTypeBinding("Lnet/dean/jraw/models/Comment;") {
    }
    object CommentModel : BoostTypeBinding("Lcom/rubenmayayo/reddit/models/reddit/CommentModel;") {
        val commentModelDeserialize get() = method("commentModelDeserialize(Lnet/dean/jraw/models/Comment;)Lcom/rubenmayayo/reddit/models/reddit/CommentModel;")
    }
    object CommentViewHolder : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/adapters/CommentViewHolder;") {
        val bind get() = method("bind(Lcom/rubenmayayo/reddit/models/reddit/CommentModel;ZZZLcom/bumptech/glide/RequestManager;)V")
        val comment get() = field("comment:Lcom/rubenmayayo/reddit/models/reddit/CommentModel;")
        val commentTv get() = field("commentTv:Lcom/rubenmayayo/reddit/ui/customviews/TableTextView;")
        val distinguishedTv get() = field("distinguishedTv:Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText;")
        val isCollapsed get() = method("isCollapsed()Z")
    }
    object ContributionModel : BoostTypeBinding("Lcom/rubenmayayo/reddit/models/reddit/ContributionModel;") {
        val construct get() = method("<init>(Landroid/os/Parcel;)V")
        val writeToParcel get() = method("writeToParcel(Landroid/os/Parcel;I)V")
    }
    object CoordinatorLayout : BoostTypeBinding("Landroidx/coordinatorlayout/widget/CoordinatorLayout;") {
    }
    object CoordinatorLayoutBehavior : BoostTypeBinding("Landroidx/coordinatorlayout/widget/CoordinatorLayout\$Behavior;") {
        val onNestedPreScroll get() = method("onNestedPreScroll(Landroidx/coordinatorlayout/widget/CoordinatorLayout;Landroid/view/View;Landroid/view/View;II[II)V")
    }
    object DrawerActivity : BoostTypeBinding("Lapp/morphe/extension/boost/api/DrawerActivity;") {
        val setBottomNavigationVisible get() = method("setBottomNavigationVisible(Z)V")
        val setProfileAvatar get() = method("setProfileAvatar(Lapp/morphe/extension/boost/api/ProfileAvatarDrawable;)V")
        val setupBottomNavigation get() = method("setupBottomNavigation(I)V")
        val updateInboxCount get() = method("updateInboxCount(I)V")
    }
    object ProfileAvatarDrawable : BoostTypeBinding("Lapp/morphe/extension/boost/api/ProfileAvatarDrawable;") {
        val construct get() = method("<init>(Landroid/graphics/Bitmap;)V")
        val bitmap get() = field("bitmap:Landroid/graphics/Bitmap;")
    }
    object EmptyRecyclerView : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/customviews/EmptyRecyclerView;") {
        val emptyRecyclerViewBottomNavigationPadding get() = method("emptyRecyclerViewBottomNavigationPadding()V")
    }
    object FirebaseApp : BoostTypeBinding("Lcom/google/firebase/FirebaseApp;") {
        val getInstance get() = method("getInstance()Lcom/google/firebase/FirebaseApp;")
    }
    object FirebaseCrashlytics : BoostTypeBinding("Lapp/morphe/extension/boost/api/FirebaseCrashlytics;") {
        val setCollectionEnabled get() = method("setCollectionEnabled(Z)V")
    }
    object FlairModel : BoostTypeBinding("Lcom/rubenmayayo/reddit/models/reddit/FlairModel;") {
    }
    object FloatingActionButton : BoostTypeBinding("Lcom/google/android/material/floatingactionbutton/FloatingActionButton;") {
    }
    object FloatingActionMenu : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/customviews/fab/FloatingActionMenu;") {
    }
    object FormattingBar : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/customviews/FormattingBar;") {
        val construct get() = method("<init>(Landroid/content/Context;Landroid/util/AttributeSet;)V")
        val formatImage get() = method("formatImage(Landroid/view/View;)V")
        val formattingBarInsertMarkdown get() = method("formattingBarInsertMarkdown(Ljava/lang/String;)V")
        val formattingBarOpenLinkDialog get() = method("formattingBarOpenLinkDialog(Ljava/lang/String;Ljava/lang/String;)V")
        val formattingBarOpenLinkDialogWithUrl get() = method("formattingBarOpenLinkDialogWithUrl(Ljava/lang/String;)V")
        val imageMenu get() = field("imageMenu:Lcom/afollestad/materialdialogs/MaterialDialog;")
        val setEditText get() = method("setEditText(Landroid/widget/EditText;)V")
    }
    object FormattingImageMenuListener : BoostTypeBinding("Lapp/morphe/extension/boost/api/FormattingImageMenuListener;") {
        val formattingBar get() = field("formattingBar:Lcom/rubenmayayo/reddit/ui/customviews/FormattingBar;")
        val formattingBarHandleImageMenuOption get() = method("formattingBarHandleImageMenuOption(Lcom/rubenmayayo/reddit/ui/customviews/menu/MenuOption;)V")
    }
    object Fragment : BoostTypeBinding("Landroidx/fragment/app/Fragment;") {
    }
    object GalleryActivity : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/activities/GalleryActivity;") {
    }
    object GalleryActivityImagePagerAdapter : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/activities/GalleryActivity\$ImagePagerAdapter;") {
        val activity get() = field("activity:Lcom/rubenmayayo/reddit/ui/activities/GalleryActivity;")
        val galleryPageFactory get() = method("galleryPageFactory(I)Landroidx/fragment/app/Fragment;")
    }
    object GalleryPageListener : BoostTypeBinding("Lapp/morphe/extension/boost/api/GalleryPageListener;") {
        val activity get() = field("activity:Lcom/rubenmayayo/reddit/ui/activities/GalleryActivity;")
        val galleryPageSelected get() = method("galleryPageSelected(I)V")
        val galleryPagerState get() = method("galleryPagerState(I)V")
    }
    object HttpUrlFetcher : BoostTypeBinding("Lcom/bumptech/glide/load/data/HttpUrlFetcher;") {
        val connection get() = field("connection:Ljava/net/HttpURLConnection;")
        val loadDataWithRedirects get() = method("loadDataWithRedirects(Ljava/net/URL;ILjava/net/URL;Ljava/util/Map;)Ljava/io/InputStream;")
        val responseCode get() = method("responseCode(Ljava/net/HttpURLConnection;)I")
        val stream get() = field("stream:Ljava/io/InputStream;")
    }
    object ImageActivity : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/activities/ImageActivity;") {
        val onCreate get() = method("onCreate(Landroid/os/Bundle;)V")
    }
    object ImageActivity2 : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/activities/ImageActivity2;") {
        val onCreate get() = method("onCreate(Landroid/os/Bundle;)V")
    }
    object ImageModelFragment : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/fragments/imagemodel/ImageModelFragment;") {
        val onCreateView get() = method("onCreateView(Landroid/view/LayoutInflater;Landroid/view/ViewGroup;Landroid/os/Bundle;)Landroid/view/View;")
        val onDestroy get() = method("onDestroy()V")
    }
    object JrawUtils : BoostTypeBinding("Lnet/dean/jraw/util/JrawUtils;") {
        val newUrl get() = method("newUrl(Ljava/lang/String;)Ljava/net/URL;")
    }
    object JsonModel : BoostTypeBinding("Lnet/dean/jraw/models/JsonModel;") {
        val data get() = method("data(Ljava/lang/String;)Ljava/lang/String;")
    }
    object LinkMenu : BoostTypeBinding("Lapp/morphe/extension/boost/api/LinkMenu;") {
        val context get() = field("context:Landroid/content/Context;")
        val dialog get() = field("dialog:Lcom/afollestad/materialdialogs/MaterialDialog;")
        val linkBuildContextMenu get() = method("linkBuildContextMenu(Ljava/lang/String;)V")
        val url get() = field("url:Ljava/lang/String;")
    }
    object LinkMenuListener : BoostTypeBinding("Lapp/morphe/extension/boost/api/LinkMenu\$Listener;") {
        val linkMenu get() = field("linkMenu:Lapp/morphe/extension/boost/api/LinkMenu;")
        val onClickContextMenu get() = method("onClickContextMenu(Lcom/rubenmayayo/reddit/ui/customviews/menu/MenuOption;)V")
    }
    object LinkTextView : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/customviews/LinkTextView;") {
        val setTextHtmlParsed get() = method("setTextHtmlParsed(Ljava/lang/String;)V")
    }
    object MainActivity : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/submissions/subreddit/MainActivity;") {
        val onResume get() = method("onResume()V")
    }
    object MaterialDialog : BoostTypeBinding("Lcom/afollestad/materialdialogs/MaterialDialog;") {
        val dismiss get() = method("dismiss()V")
    }
    object MediaImageActivity : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/activities/MediaImageActivity;") {
        val onCreate get() = method("onCreate(Landroid/os/Bundle;)V")
    }
    object MediaUploadFactory : BoostTypeBinding("Lapp/morphe/extension/boost/api/MediaUploadFactory;") {
        val create get() = method("create(Ljava/lang/String;)Lapp/morphe/extension/boost/api/MediaUploader;")
    }
    object MediaUploader : BoostTypeBinding("Lapp/morphe/extension/boost/api/MediaUploader;") {
    }
    object MediaVideoActivity : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/activities/MediaVideoActivity;") {
        val onCreate get() = method("onCreate(Landroid/os/Bundle;)V")
    }
    object MenuOption : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/customviews/menu/MenuOption;") {
        val construct get() = method("<init>()V")
        val iconResource get() = field("iconResource:I")
        val id get() = field("id:I")
        val titleResource get() = field("titleResource:I")
    }
    object MessagesActivity : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/messages/MessagesActivity;") {
        val onResume get() = method("onResume()V")
    }
    object MyApplication : BoostTypeBinding("Lcom/rubenmayayo/reddit/MyApplication;") {
        val onCreate get() = method("onCreate()V")
    }
    object Navigation : BoostTypeBinding("Lapp/morphe/extension/boost/api/Navigation;") {
        val handleNavigation get() = method("handleNavigation(Landroid/content/Context;Ljava/lang/String;Z)Z")
        val openUrl get() = method("openUrl(Landroid/content/Context;Ljava/lang/String;)V")
    }
    object OAuthData : BoostTypeBinding("Lnet/dean/jraw/http/oauth/OAuthData;") {
        val getAccessToken get() = method("getAccessToken()Ljava/lang/String;")
    }
    object Paginator : BoostTypeBinding("Lnet/dean/jraw/paginators/Paginator;") {
    }
    object RecyclerViewViewHolder : BoostTypeBinding("Landroidx/recyclerview/widget/RecyclerView\$ViewHolder;") {
    }
    object RedditJraw : BoostTypeBinding("Lapp/morphe/extension/boost/api/RedditJraw;") {
        val loadSubmissions get() = method("loadSubmissions(Lnet/dean/jraw/paginators/Paginator;Z)Ljava/util/ArrayList;")
    }
    object RemoteConfigHelper : BoostTypeBinding("Lapp/morphe/extension/boost/api/RemoteConfigHelper;") {
        val isEnabled get() = method("isEnabled(Ljava/lang/String;)Z")
        val remoteConfigString get() = method("remoteConfigString(Ljava/lang/String;)Ljava/lang/String;")
    }
    object RequestManager : BoostTypeBinding("Lcom/bumptech/glide/RequestManager;") {
    }
    object RichFlairView : BoostTypeBinding("Lcom/rubenmayayo/reddit/models/reddit/RichFlairView;") {
        val flairColors get() = method("flairColors(Lcom/rubenmayayo/reddit/models/reddit/FlairModel;Z)V")
        val setTextColor get() = method("setTextColor(I)V")
    }
    object ScrollAwareFABBehavior : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/customviews/ScrollAwareFABBehavior;") {
        val fabNestedScroll get() = method("fabNestedScroll(Landroidx/coordinatorlayout/widget/CoordinatorLayout;Landroid/view/View;Landroid/view/View;IIII)V")
    }
    object ScrollAwareFABBehaviorBottomNavigation : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/customviews/ScrollAwareFABBehaviorBottomNavigation;") {
        val onDependentViewChanged get() = method("onDependentViewChanged(Landroidx/coordinatorlayout/widget/CoordinatorLayout;Landroid/view/View;Landroid/view/View;)Z")
        val onDependentViewRemoved get() = method("onDependentViewRemoved(Landroidx/coordinatorlayout/widget/CoordinatorLayout;Landroid/view/View;Landroid/view/View;)V")
    }
    object ScrollAwareFABMenuBehavior : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/customviews/ScrollAwareFABMenuBehavior;") {
        val fabMenuNestedScroll get() = method("fabMenuNestedScroll(Landroidx/coordinatorlayout/widget/CoordinatorLayout;Landroid/view/View;Landroid/view/View;IIII)V")
        val onDependentViewChanged get() = method("onDependentViewChanged(Landroidx/coordinatorlayout/widget/CoordinatorLayout;Landroid/view/View;Landroid/view/View;)Z")
        val onDependentViewRemoved get() = method("onDependentViewRemoved(Landroidx/coordinatorlayout/widget/CoordinatorLayout;Landroid/view/View;Landroid/view/View;)V")
    }
    object ScrollAwareFABMiniBehavior : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/customviews/ScrollAwareFABMiniBehavior;") {
        val fabMiniNestedScroll get() = method("fabMiniNestedScroll(Landroidx/coordinatorlayout/widget/CoordinatorLayout;Landroid/view/View;Landroid/view/View;IIII)V")
    }
    object SearchAbstractActivity : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/search/SearchAbstractActivity;") {
        val onCreate get() = method("onCreate(Landroid/os/Bundle;)V")
        val rows get() = field("rows:Ljava/util/ArrayList;")
        val searchEditText get() = field("searchEditText:Landroid/widget/EditText;")
    }
    object SearchAbstractActivitySubredditErrorCallback : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/search/SearchAbstractActivity\$SubredditErrorCallback;") {
        val activity get() = field("activity:Lcom/rubenmayayo/reddit/ui/search/SearchAbstractActivity;")
        val onError get() = method("onError(Ljava/lang/Exception;)V")
    }
    object SearchGenericActivity : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/search/SearchGenericActivity;") {
        val onCreate get() = method("onCreate(Landroid/os/Bundle;)V")
        val searchGenericActivityDefaultGoTo get() = method("searchGenericActivityDefaultGoTo()V")
    }
    object SearchSubredditActivity : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/subreddits/SearchSubredditActivity;") {
        val onCreate get() = method("onCreate(Landroid/os/Bundle;)V")
    }
    object SettingsActivityCompat : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/preferences/v2/SettingsActivityCompat;") {
        val onCreate get() = method("onCreate(Landroid/os/Bundle;)V")
    }
    object Snackbar : BoostTypeBinding("Lcom/google/android/material/snackbar/Snackbar;") {
        val snackbarShow get() = method("snackbarShow()V")
        val styleAttributes get() = field("styleAttributes:[I")
    }
    object StateSubmissionView : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/customviews/StateSubmissionView;") {
        val headerText get() = field("headerText:Landroid/widget/TextView;")
        val setState get() = method("setState(Lcom/rubenmayayo/reddit/models/reddit/SubmissionModel;)V")
        val summaryText get() = field("summaryText:Landroid/widget/TextView;")
    }
    object Submission : BoostTypeBinding("Lnet/dean/jraw/models/Submission;") {
    }
    object SubmissionFragment : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/fragments/SubmissionFragment;") {
        val submissions get() = field("submissions:Ljava/util/ArrayList;")
    }
    object SubmissionModel : BoostTypeBinding("Lcom/rubenmayayo/reddit/models/reddit/SubmissionModel;") {
        val fromSubmission get() = method("fromSubmission(Lnet/dean/jraw/models/Submission;)Lcom/rubenmayayo/reddit/models/reddit/SubmissionModel;")
        val removalCategory get() = field("removalCategory:Ljava/lang/String;")
    }
    object SubmissionRecyclerViewFragment : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/fragments/SubmissionRecyclerViewFragment;") {
        val appendSubmissions get() = method("appendSubmissions(Ljava/util/ArrayList;)V")
        val feedActionS1InvalidIndex get() = method("feedActionS1InvalidIndex(I)V")
        val hideFragment get() = method("hideFragment(Lcom/rubenmayayo/reddit/models/reddit/SubmissionModel;)V")
        val mRecyclerView get() = field("mRecyclerView:Lcom/rubenmayayo/reddit/ui/customviews/EmptyRecyclerView;")
        val setSubmissions get() = method("setSubmissions(Ljava/util/ArrayList;)V")
    }
    object SubmissionRemoveClickListener : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/fragments/SubmissionRemoveClickListener;") {
        val construct get() = method("<init>(Lcom/rubenmayayo/reddit/ui/fragments/SubmissionFragment;)V")
    }
    object SubmissionViewHolder : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/adapters/SubmissionViewHolder;") {
        val bind get() = method("bind(Lcom/rubenmayayo/reddit/models/reddit/SubmissionModel;ZZZLcom/bumptech/glide/RequestManager;)V")
        val infoSeparator get() = field("infoSeparator:Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText\$Part;")
        val infoTop get() = field("infoTop:Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText;")
    }
    object SubmitGalleryFragment : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/submit/v2/SubmitGalleryFragment;") {
        val submitGallerySubmissionKind get() = method("submitGallerySubmissionKind()Lnet/dean/jraw/managers/SubmissionKind;")
    }
    object SubredditActivity : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/submissions/subreddit/SubredditActivity;") {
        val onCreate get() = method("onCreate(Landroid/os/Bundle;)V")
    }
    object SubredditBaseActivity : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/submissions/subreddit/SubredditBaseActivity;") {
        val onHideToggle get() = method("onHideToggle(ILcom/rubenmayayo/reddit/models/reddit/SubmissionModel;Z)V")
    }
    object TableTextView : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/customviews/TableTextView;") {
    }
    object ThingFragment : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/fragments/ThingFragment;") {
        val filteredNextPageSnackbar get() = method("filteredNextPageSnackbar(Landroid/view/View;)V")
    }
    object ThingFragmentListener : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/fragments/ThingFragment\$Listener;") {
        val onHideToggle get() = method("onHideToggle(ILcom/rubenmayayo/reddit/models/reddit/SubmissionModel;Z)V")
    }
    object Timber : BoostTypeBinding("Lapp/morphe/extension/boost/api/Timber;") {
        val exceptionHandler get() = method("exceptionHandler(Ljava/lang/Throwable;)V")
        val timber get() = method("timber(Ljava/lang/String;[Ljava/lang/Object;)V")
    }
    object TrendingTodayAdapter : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/sidebar/today/TrendingTodayAdapter;") {
        val onBindViewHolder get() = method("onBindViewHolder(Landroidx/recyclerview/widget/RecyclerView\$ViewHolder;I)V")
        val rows get() = field("rows:Ljava/util/List;")
    }
    object TrendingTodayAsyncLoader : BoostTypeBinding("Lapp/morphe/extension/boost/api/TrendingTodayAsyncLoader;") {
        val doInBackground get() = method("doInBackground([Ljava/lang/Object;)Ljava/lang/Object;")
    }
    object TrendingTodayFragment : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/sidebar/today/TrendingTodayFragment;") {
        val onViewCreated get() = method("onViewCreated(Landroid/view/View;Landroid/os/Bundle;)V")
    }
    object UpdateImageWidget : BoostTypeBinding("Lapp/morphe/extension/boost/api/UpdateImageWidget;") {
        val imageWidgetProviderUpdateWithSubmission get() = method("imageWidgetProviderUpdateWithSubmission(ILcom/rubenmayayo/reddit/models/reddit/SubmissionModel;Ljava/lang/String;)V")
    }
    object UserActivity : BoostTypeBinding("Lcom/rubenmayayo/reddit/ui/profile/UserActivity;") {
        val onCreate get() = method("onCreate(Landroid/os/Bundle;)V")
        val tabRoutes get() = field("tabRoutes:[Ljava/lang/String;")
    }
    object Utils : BoostTypeBinding("Lapp/morphe/extension/boost/api/Utils;") {
        val imageWidgetThumbnailSize get() = method("imageWidgetThumbnailSize()I")
    }
}
