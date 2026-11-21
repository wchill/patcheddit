/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.boost.navigation

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import app.morphe.extension.boost.api.DrawerActivity
import app.morphe.extension.shared.settings.MatureContentLock
import com.rubenmayayo.reddit.models.reddit.SubscriptionViewModel
import net.dean.jraw.ApiException
import net.dean.jraw.http.NetworkException
import net.dean.jraw.http.oauth.InvalidScopeException
import org.json.JSONException
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.WeakHashMap
import java.util.concurrent.Executors

object BoostDrawerSubscriptions {
    private const val TAG = "MorpheDrawer"
    private const val BATCH_SIZE = 25
    private const val SAFE_TTL_MS = 15 * 60_000L
    private const val RETRY_DELAY_MS = 60_000L
    private val builtInFeeds = setOf(
        "all", "popular", "_load_front_page_this_is_not_a_subreddit",
        "_load_saved_this_is_not_a_subreddit", "_load_history_this_is_not_a_subreddit",
    )
    private val subredditName = Regex("[a-z0-9_]{1,64}")
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val checks = mutableMapOf<String, Check>()
    private val pending = mutableSetOf<String>()
    private val waitingDrawers = WeakHashMap<DrawerActivity, Set<String>>()

    private data class Check(val safe: Boolean, val expiresAt: Long)

    @JvmStatic
    @Synchronized
    fun filter(activity: DrawerActivity, subscriptions: ArrayList<SubscriptionViewModel>): ArrayList<SubscriptionViewModel> {
        if (!MatureContentLock.isLocked()) return subscriptions

        val now = SystemClock.elapsedRealtime()
        val unresolved = linkedSetOf<String>()
        val visible = ArrayList<SubscriptionViewModel>(subscriptions.size)
        for (subscription in subscriptions) {
            val name = subscription.community.lowercase(Locale.ROOT)
            if (subscription.isMultireddit || name in builtInFeeds) {
                visible.add(subscription)
                continue
            }
            val check = checks[name]
            if (check != null && check.expiresAt > now) {
                if (check.safe) visible.add(subscription)
            } else if (subredditName.matches(name)) {
                // Boost's cached subscription names do not include their mature-content status.
                unresolved.add(name)
            }
        }

        if (unresolved.isEmpty()) waitingDrawers.remove(activity)
        else waitingDrawers[activity] = unresolved
        val requested = unresolved.filter(pending::add)
        for (batch in requested.chunked(BATCH_SIZE)) {
            worker.execute {
                val safe = try {
                    BoostNativeListings.safeCommunities(batch)
                } catch (_: NetworkException) {
                    Log.w(TAG, "Reddit rejected the Boost sidebar metadata request.")
                    emptySet()
                } catch (_: IOException) {
                    Log.w(TAG, "Could not read Boost sidebar metadata from Reddit.")
                    emptySet()
                } catch (_: JSONException) {
                    Log.w(TAG, "Boost received invalid sidebar metadata from Reddit.")
                    emptySet()
                } catch (_: ApiException) {
                    Log.w(TAG, "The Reddit API could not verify Boost sidebar communities.")
                    emptySet()
                } catch (_: InvalidScopeException) {
                    Log.w(TAG, "Boost's Reddit connection lacks permission to verify sidebar communities.")
                    emptySet()
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "Boost's Reddit client could not verify sidebar communities.", e)
                    emptySet()
                }
                main.post { finishLookup(batch, safe) }
            }
        }
        return visible
    }

    private fun finishLookup(batch: List<String>, safe: Set<String>) {
        val now = SystemClock.elapsedRealtime()
        val redraw = synchronized(this) {
            pending.removeAll(batch.toSet())
            for (name in batch) {
                val confirmedSafe = name in safe
                checks[name] = Check(confirmedSafe, now + if (confirmedSafe) SAFE_TTL_MS else RETRY_DELAY_MS)
            }
            waitingDrawers.entries
                .filter { (_, unresolved) -> unresolved.any(safe::contains) }
                .map { WeakReference(it.key) }
        }
        for (reference in redraw) {
            val activity = reference.get() ?: continue
            if (!activity.isFinishing && !activity.isDestroyed) activity.reloadDrawerSubscriptions()
        }
    }
}
