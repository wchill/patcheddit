/*
 * Copyright 2026 brealorg.
 * Modifications Copyright 2026 wchill.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.boost.navigation

import MorpheBoostNativeStatics
import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuBuilder.Callback as MenuBuilderCallback
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams as CoordinatorLayoutParams
import com.google.android.material.navigation.NavigationBarItemView
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.behavior.HideBottomViewOnScrollBehavior
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.rubenmayayo.reddit.aa.User
import com.rubenmayayo.reddit.ui.activities.BaseActivity as BoostActivity
import com.rubenmayayo.reddit.ui.customviews.dialogs.UserSelectorView
import com.rubenmayayo.reddit.ui.submissions.subreddit.SubredditActivity
import com.afollestad.materialdialogs.MaterialDialog.Builder as MaterialDialogBuilder
import com.google.android.material.shape.MaterialShapeDrawable
import app.morphe.extension.boost.api.RedditJraw
import app.morphe.extension.boost.api.DrawerActivity

/** Typed bindings to the official Boost UI, not upstream library headers. */
object BoostNativeNavigation {
    interface SelectionCallback {
        fun onSelected(item: MenuItem): Boolean
        fun onReselected(item: MenuItem)
    }

    interface ProfileCallback {
        fun onAddAccount()
        fun onAccountSelected(username: String?, anonymous: Boolean, index: Int)
    }

    @JvmStatic
    fun menu(view: View): Menu = checkNotNull((view as BottomNavigationView).getMenu()) {
        "Boost navigation menu is not initialized"
    }

    @JvmStatic
    fun setPresentation(view: View) {
        val navigation = view as BottomNavigationView
        navigation.setLabelVisibilityMode(2)
        navigation.setItemHorizontalTranslationEnabled(false)
    }

    @JvmStatic
    fun setTint(view: View, tint: ColorStateList) {
        val navigation = view as BottomNavigationView
        navigation.setItemIconTintList(tint)
        navigation.setItemTextColor(tint)
    }

    @JvmStatic
    fun setItemIconTint(view: View, tint: ColorStateList) {
        (view as NavigationBarItemView).setIconTintList(tint)
    }

    @JvmStatic
    fun fillColor(drawable: Drawable): ColorStateList? =
        (drawable as? MaterialShapeDrawable)?.drawableState?.fillColor

    @JvmStatic
    fun setSelectionCallback(view: View, callback: SelectionCallback) {
        val navigation = view as BottomNavigationView
        val nativeMenu = menu(view) as MenuBuilder
        // R8 erased NavigationBarView's listener methods and hard-casts their
        // implementations. The retained MenuBuilder callback is extensible.
        nativeMenu.setCallback(object : MenuBuilderCallback {
            override fun onMenuItemSelected(menu: MenuBuilder?, item: MenuItem?): Boolean {
                val selected = checkNotNull(item) {
                    "Boost navigation callback did not supply a menu item"
                }
                if (selected.itemId == navigation.getSelectedItemId()) {
                    callback.onReselected(selected)
                    return true
                }
                return !callback.onSelected(selected)
            }

            override fun onMenuModeChange(menu: MenuBuilder?) = Unit
        })
    }

    @JvmStatic
    fun coordinatorBehavior(view: View): Any? =
        (view.layoutParams as CoordinatorLayoutParams).mBehavior

    @JvmStatic
    fun applyHideOnScroll(view: View, revealScrolledBar: Boolean): Boolean {
        val enabled = MorpheBoostNativeStatics.BoostPreferences_getInstance().preferences
            .getBoolean("pref_bottom_navigation_hide_on_scroll", true)
        val params = view.layoutParams as CoordinatorLayoutParams
        var behavior = params.mBehavior
        if (enabled) {
            if (revealScrolledBar && view.translationY != 0f) {
                view.clearAnimation()
                view.animate().cancel()
                view.translationY = 0f
                behavior = null
            }
            if (behavior !is HideBottomViewOnScrollBehavior) {
                params.setBehavior(HideBottomViewOnScrollBehavior())
            }
        } else {
            view.clearAnimation()
            view.animate().cancel()
            params.setBehavior(null)
            view.translationY = 0f
        }
        view.layoutParams = params
        return enabled
    }

    @JvmStatic
    fun applyInboxBadge(view: View, itemId: Int, count: Int) {
        val navigation = view as BottomNavigationView
        val menuView = navigation.menuView
        val itemView = navigation.findViewById<View>(itemId) as NavigationBarItemView?
        if (count <= 0) {
            val badge = itemView?.badgeDrawable
            if (itemView != null && badge != null) {
                itemView.setClipChildren(true)
                itemView.setClipToPadding(true)
                val parent = badge.getCustomBadgeParent()
                if (parent == null) {
                    itemView.icon.overlay.remove(badge)
                } else {
                    parent.foreground = null
                }
                itemView.badgeDrawable = null
            }
            menuView.badges.put(itemId, null)
            return
        }

        val badge = (menuView.badges.get(itemId) as BadgeDrawable?)
            ?: BadgeDrawable(navigation.context, null).also { menuView.badges.put(itemId, it) }
        itemView?.setBadge(badge)

        // Boost inlined BadgeDrawable.setNumber/setVisible into DrawerActivity.d2.
        val state = badge.state
        val number = maxOf(0, count)
        if (state.currentState.number != number) {
            state.overridingState.number = number
            state.currentState.number = number
            if (!state.hasNumber()) {
                badge.textDrawableHelper.textSizeDirty = true
                badge.refreshNumberGeometry()
                badge.refreshNumberRendering()
                badge.invalidateSelf()
            }
        }
        state.overridingState.visible = true
        state.currentState.visible = true
        badge.setVisible(true, false)
    }

    @JvmStatic
    fun isDrawerActivity(activity: Activity): Boolean = activity is DrawerActivity

    @JvmStatic
    fun profileLongPress(activity: Activity): View.OnLongClickListener =
        View.OnLongClickListener {
            (activity as DrawerActivity).onNavigationLongPress(4, false)
            true
        }

    @JvmStatic
    fun subscriptionsLongPress(activity: Activity): View.OnLongClickListener =
        View.OnLongClickListener {
            (activity as DrawerActivity).onNavigationLongPress(2, false)
            true
        }

    @JvmStatic
    fun showProfileSelector(
        activity: Activity,
        titleResource: Int,
        callback: ProfileCallback,
    ): Dialog {
        val selector = UserSelectorView(activity, true)
        selector.setCallback(object : UserSelectorView.Callback {
            override fun onAddAccount() = callback.onAddAccount()

            override fun onAccountSelected(user: User?, index: Int) =
                callback.onAccountSelected(user?.name, user == null, index)
        })
        val builder = MaterialDialogBuilder(activity)
        builder.title(titleResource)
        builder.customView(selector, false)
        return checkNotNull(builder.show()) {
            "Boost profile selector dialog was not created"
        }
    }

    @JvmStatic
    fun addAccount(activity: Activity) = MorpheBoostNativeStatics.Navigation_addAccount(activity)

    @JvmStatic
    fun selectAccount(activity: Activity, index: Int) =
        (activity as DrawerActivity).selectAccount(index)

    @JvmStatic
    fun isLoggedIn(): Boolean = RedditJraw.instance.user != null

    @JvmStatic
    fun currentUsername(): String? = RedditJraw.instance.username()

    @JvmStatic
    fun openProfile(activity: Activity, username: String) =
        (activity as BoostActivity).openProfile(username)

    @JvmStatic
    fun currentCommunity(activity: Activity?): String? =
        if (activity is DrawerActivity) activity.currentCommunity() else null

    @JvmStatic
    fun openCurrentSubredditSubscriptions(activity: Activity): Boolean =
        (activity as SubredditActivity).openSubscriptions()

    @JvmStatic
    fun tintToolbar(view: View, color: Int) {
        if (view is Toolbar) {
            view.setTitleTextColor(color)
            view.setSubtitleTextColor(color)
            view.getNavigationIcon()?.let {
                it.mutate().setTint(color)
                view.setNavigationIcon(it)
            }
            view.getOverflowIcon()?.let {
                it.mutate().setTint(color)
                view.setOverflowIcon(it)
            }
        } else if (view is android.widget.Toolbar) {
            view.setTitleTextColor(color)
            view.setSubtitleTextColor(color)
            view.navigationIcon?.let {
                it.mutate().setTint(color)
                view.navigationIcon = it
            }
            view.overflowIcon?.let {
                it.mutate().setTint(color)
                view.overflowIcon = it
            }
        }
    }
}
