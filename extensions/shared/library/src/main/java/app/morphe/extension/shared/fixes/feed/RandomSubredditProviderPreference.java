/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.fixes.feed;

import android.content.Context;
import android.preference.ListPreference;
import android.util.AttributeSet;

@SuppressWarnings("deprecation")
public final class RandomSubredditProviderPreference extends ListPreference {
    public RandomSubredditProviderPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setEntries(new CharSequence[]{"Reddit search", "RedditRand (third-party service)"});
        setEntryValues(new CharSequence[]{RandomSubreddit.REDDIT_SEARCH, RandomSubreddit.REDDIT_RAND});
        setDefaultValue(RandomSubreddit.DEFAULT_PROVIDER);
    }

    @Override
    public CharSequence getSummary() {
        String provider = getValue();
        if (provider == null) provider = RandomSubreddit.DEFAULT_PROVIDER;
        return RandomSubreddit.REDDIT_RAND.equals(provider) ? getEntries()[1] : getEntries()[0];
    }
}
