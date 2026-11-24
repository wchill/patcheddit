/*
 * Copyright 2026 brealorg.
 * Modifications Copyright 2026 wchill.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (82d70f7).
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.boost.navigation

import MorpheBoostNativeStatics
import android.app.Activity
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView.ViewHolder as RecyclerViewViewHolder
import app.morphe.extension.boost.api.SearchOption
import com.rubenmayayo.reddit.models.reddit.SubmissionModel
import com.rubenmayayo.reddit.models.reddit.SubredditModel
import com.rubenmayayo.reddit.models.reddit.SubscriptionViewModel
import com.rubenmayayo.reddit.ui.fragments.SubmissionRecyclerViewFragment
import com.rubenmayayo.reddit.ui.search.SearchAbstractActivity
import com.rubenmayayo.reddit.ui.sidebar.today.TrendingTodayFragment
import net.dean.jraw.RedditClient
import net.dean.jraw.models.Submission
import net.dean.jraw.models.JsonModel
import net.dean.jraw.paginators.Sorting
import net.dean.jraw.paginators.SubredditPaginator
import app.morphe.extension.boost.api.RedditJraw
import app.morphe.extension.boost.api.TrendingSearch
import org.json.JSONObject
import java.util.Locale

/** Typed bindings for Boost 1.14.1's native JRAW, search rows and sidebar loader. */
object BoostNativeListings {
    private fun client(): RedditClient = checkNotNull(RedditJraw.instance.client) {
        "Boost RedditJRAW client is not initialized"
    }

    @JvmStatic
    fun hotSubmissions(source: String, limit: Int): List<Submission> {
        val paginator = SubredditPaginator(client(), source, *emptyArray<String>())
        paginator.setSorting(Sorting.HOT)
        paginator.setLimit(limit)
        return paginator.next().toList()
    }

    @JvmStatic
    fun fetchSubreddit(name: String): SubredditModel =
        SubredditModel(client().getSubreddit(name))

    fun safeCommunities(names: List<String>): Set<String> {
        val reddit = checkNotNull(RedditJraw.instance) { "Boost RedditJRAW is not initialized" }
        val client = checkNotNull(reddit.client) { "Boost RedditJRAW client is not initialized" }
        reddit.ensureAuthenticated()
        val request = client.request().path("/api/info").query("sr_name", names.joinToString(","), "raw_json", "1")
            .build()
        val response = checkNotNull(client.execute(request)) { "Boost's community lookup returned no response" }
        val json = JSONObject(checkNotNull(response.raw) { "Boost's community lookup returned no body" })
        val children = json.getJSONObject("data").getJSONArray("children")
        val requested = names.toSet()
        return buildSet {
            for (index in 0 until children.length()) {
                val child = children.optJSONObject(index) ?: continue
                if (child.optString("kind") != "t5") continue
                val data = child.optJSONObject("data") ?: continue
                val name = (data.opt("display_name") as? String)?.lowercase(Locale.ROOT) ?: continue
                val flags = listOfNotNull(data.opt("over18"), data.opt("over_18"))
                if (name in requested && flags.isNotEmpty() && flags.all { it == false }) add(name)
            }
        }
    }

    @JvmStatic
    fun searchRow(name: String, model: SubredditModel): Any =
        SearchOption(14, name).apply {
            subscription = SubscriptionViewModel(model)
            subreddit = model
        }

    @JvmStatic
    fun searchHeader(title: String): Any = SearchOption(6, title)

    @JvmStatic
    fun notifySearchRows(activity: Activity) {
        checkNotNull((activity as SearchAbstractActivity).searchAdapter) {
            "Boost search adapter is not initialized"
        }.notifyDataSetChanged()
    }

    @JvmStatic
    fun nativeTrendingRows(): List<*> = checkNotNull(RedditJraw.instance.trendingRows()) {
        "Boost native trending loader returned null"
    }

    @JvmStatic
    fun trendingSubmissions(row: Any): List<SubmissionModel> =
        (row as TrendingSearch).submissions ?: emptyList()

    @JvmStatic
    fun submissionModel(submission: Submission): SubmissionModel =
        MorpheBoostNativeStatics.SubmissionModel_fromSubmission(submission)

    @JvmStatic
    fun trendingRow(title: String, model: SubmissionModel): Any =
        TrendingSearch().apply {
            this.title = title
            submissions = arrayListOf(model)
        }

    @JvmStatic
    fun includeNsfw(): Boolean {
        val preferences = MorpheBoostNativeStatics.BoostPreferences_getInstance()
        return preferences.preferences.getBoolean("pref_search_include_over_18", preferences.includeNsfw())
    }

    @JvmStatic
    fun isPromoted(submission: JsonModel): Boolean =
        submission.data("promoted", Boolean::class.javaObjectType) == true ||
            submission.data("is_ad", Boolean::class.javaObjectType) == true

    @JvmStatic
    fun fragmentActivity(fragment: Any?): Activity? =
        (fragment as Fragment?)?.getActivity()

    @JvmStatic
    fun reloadTrending(fragment: Any) {
        val target = fragment as TrendingTodayFragment
        checkNotNull(target.viewModel) { "Boost trending view model is not initialized" }
        // The old ViewModel.h() was inlined into this retained native Runnable.
        TrendingTodayFragment.ReloadTask(target).run()
    }

    @JvmStatic
    fun holderView(holder: Any): View = (holder as RecyclerViewViewHolder).itemView

    @JvmStatic
    fun refreshFeed(fragment: Any) =
        (fragment as SubmissionRecyclerViewFragment).onRefresh()
}
