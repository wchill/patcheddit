/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.boost.http.reddit;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.bumptech.glide.RequestManager;
import com.rubenmayayo.reddit.models.reddit.CommentModel;
import com.rubenmayayo.reddit.models.reddit.SubmissionModel;
import com.rubenmayayo.reddit.ui.adapters.CommentViewHolder;
import com.rubenmayayo.reddit.ui.adapters.SubmissionViewHolder;
import com.rubenmayayo.reddit.ui.customviews.menu.MenuOption;

import net.dean.jraw.RedditClient;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.morphe.extension.boost.api.CommentMenuListener;
import app.morphe.extension.boost.api.RedditJraw;
import app.morphe.extension.boost.navigation.BoostNativeListings;
import app.morphe.extension.boost.utils.BoostUndeleteSettings;
import app.morphe.extension.shared.archive.HttpUtils;
import app.morphe.extension.shared.debug.LoggingUtils;
import app.morphe.extension.vendored.com.fasterxml.jackson.databind.JsonNode;
import app.morphe.extension.vendored.com.fasterxml.jackson.databind.node.ObjectNode;

public final class ArcticShiftRetry {
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    // Accessed only from the main thread. Bindings never retain their holder.
    private static final Map<Object, Binding> bindings = new WeakHashMap<>();
    private static final Set<ArchiveRetryItem> loading =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private ArcticShiftRetry() {}

    public static void onBind(Object holder, Object model, boolean first, boolean second,
                              boolean third, Object glide) {
        bindings.put(holder, new Binding((ArchiveRetryItem) model, first, second, third, glide));
    }

    public static String retryJson(Object source) {
        // Do not serialize reply subtrees into every ancestor's snapshot.
        return ArchiveRecoveryState.snapshot(archiveFields(source));
    }

    public static void addMenuOption(Object holder, List<MenuOption> options) {
        Binding binding = bindings.get(holder);
        if (!BoostUndeleteSettings.isRedditUndeleteEnabled() || binding == null) return;
        String json = binding.item.morpheArchiveRetryJson();
        if (json == null) return;
        MenuOption option;
        try {
            option = ArchiveRecoveryState.isLoaded(HttpUtils.getJsonFromString(json))
                    ? new RefreshOption(holder instanceof CommentViewHolder)
                    : new ArcticShiftOption();
        } catch (RuntimeException failure) {
            LoggingUtils.logException(false, () -> "Invalid archive retry snapshot", failure);
            return;
        }
        option.enabled = !loading.contains(binding.item);
        options.add(option);
    }

    /** @return true if the option was handled and the native comment menu action must be skipped. */
    public static boolean onCommentMenuClick(Object listener, MenuOption option) {
        CommentViewHolder holder = ((CommentMenuListener) listener).holder;
        if (!onClick(holder, option)) return false;
        MaterialDialog dialog = holder.menuDialog;
        if (dialog != null) dialog.dismiss();
        return true;
    }

    /** @return true if the option was handled and the native menu action must be skipped. */
    public static boolean onClick(Object holder, MenuOption option) {
        if (!(option instanceof ReloadOption)) return false;
        Binding binding = bindings.get(holder);
        if (!BoostUndeleteSettings.isRedditUndeleteEnabled() || binding == null) return true;
        ReloadOption action = (ReloadOption) option;
        ArchiveRetryItem item = binding.item;
        String json = item.morpheArchiveRetryJson();
        if (json == null || !loading.add(item)) return true;
        boolean comment = holder instanceof CommentViewHolder;
        View view = BoostNativeListings.holderView(holder);
        Toast.makeText(view.getContext(), action.progress, Toast.LENGTH_SHORT).show();
        worker.execute(() -> {
            try {
                ObjectNode original = (ObjectNode) HttpUtils.getJsonFromString(json);
                Reloaded result = action.load(original, comment);
                ArchiveRetryItem before = parseItem(json, comment);
                ArchiveRetryItem after = parseItem(result.data.toString(), comment);
                main.post(() -> {
                    try {
                        if (!BoostUndeleteSettings.isRedditUndeleteEnabled()
                                || !json.equals(item.morpheArchiveRetryJson())) return;
                        ArchiveRetryMerge.apply(item, before, after,
                                deserializedFields(comment, result.bodyOnly));
                        // Rebind from the updated model; never edit the warning's text or spans.
                        for (Map.Entry<Object, Binding> entry : new ArrayList<>(bindings.entrySet())) {
                            Binding current = entry.getValue();
                            if (entry.getKey() != null && current.item == item) {
                                rebind(entry.getKey(), item, current.first, current.second,
                                        current.third, current.glide.get());
                            }
                        }
                        if (result.missing != null) {
                            Toast.makeText(view.getContext(), result.missing, Toast.LENGTH_LONG).show();
                        }
                    } catch (RuntimeException | ReflectiveOperationException failure) {
                        reportFailure(view, action, failure);
                    } finally {
                        loading.remove(item);
                    }
                });
            } catch (Exception failure) {
                main.post(() -> {
                    loading.remove(item);
                    reportFailure(view, action, failure);
                });
            }
        });
        return true;
    }

    private static Reloaded reloadFromArcticShift(ObjectNode original) throws Exception {
        RedditSubmissionUndeleteInterceptor.ReloadResult result =
                RedditSubmissionUndeleteInterceptor.reload(original);
        return new Reloaded(result.data, ArchiveRecoveryState.isBodyOnly(original),
                result.found ? null : "Content is not available from Arctic Shift");
    }

    private static Reloaded refreshFromReddit(ObjectNode original, boolean comment) throws Exception {
        String name = original.path("name").asText();
        if (name.isEmpty()) name = (comment ? "t1_" : "t3_") + original.path("id").asText();
        RedditJraw reddit = RedditJraw.instance;
        reddit.ensureAuthenticated();
        List<?> things = ((RedditClient) reddit.client).get(name);
        if (things.isEmpty()) {
            return new Reloaded(original, false, "Reddit did not return this " + (comment ? "comment" : "post"));
        }
        JsonNode fresh = HttpUtils.getJsonFromString(retryJson(things.get(0)));
        // Overlay, so thread-only fields absent from /api/info (such as depth) are kept.
        ObjectNode updated = original.deepCopy();
        updated.setAll((ObjectNode) fresh);
        updated.remove(List.of("replies", ArchiveRecoveryState.LOADED_KEY,
                ArchiveRecoveryState.RETRY_KEY, ArchiveRecoveryState.EXTRA_EMOJI_CONTEXT_KEY));
        if (RedditApiUtils.isContentRemoved(updated)) RedditApiUtils.setRemovalEmoji(updated);
        return new Reloaded(updated, false, null);
    }

    private static void reportFailure(View view, ReloadOption action, Exception failure) {
        LoggingUtils.logException(false, () -> action.title + " failed", failure);
        Toast.makeText(view.getContext(), action.failure, Toast.LENGTH_LONG).show();
    }

    // The patch supplies both native model conversions, keeping stock Jackson types out of extension signatures.
    private static Iterator<? extends Map.Entry<String, ?>> archiveFields(Object source) {
        throw new UnsupportedOperationException("Archive snapshot bridge is not installed");
    }

    private static ArchiveRetryItem parseItem(String json, boolean comment) {
        throw new UnsupportedOperationException("Archive model bridge is not installed");
    }

    private static void rebind(Object holder, Object model, boolean first, boolean second,
                               boolean third, Object glide) {
        if (holder instanceof CommentViewHolder) {
            ((CommentViewHolder) holder).bind((CommentModel) model, first, second, third,
                    (RequestManager) glide);
        } else {
            ((SubmissionViewHolder) holder).bind((SubmissionModel) model, first, second, third,
                    (RequestManager) glide);
        }
    }

    // Filled in by the bytecode patch from the fields assigned by Boost's native deserializers.
    private static String deserializedFields(boolean comment, boolean bodyOnly) {
        throw new UnsupportedOperationException("Archive model fields are not installed");
    }

    private abstract static class ReloadOption extends MenuOption {
        final String progress;
        final String failure;

        ReloadOption(int id, String title, int icon, String progress, String failure) {
            this.id = id;
            this.title = title;
            this.iconResource = icon;
            this.progress = progress;
            this.failure = failure;
        }

        abstract Reloaded load(ObjectNode original, boolean comment) throws Exception;
    }

    private static final class ArcticShiftOption extends ReloadOption {
        ArcticShiftOption() {
            super(109, "Reload from Arctic Shift", android.R.drawable.ic_popup_sync,
                    "Loading from Arctic Shift...", "Could not load from Arctic Shift. Try again.");
        }

        @Override
        Reloaded load(ObjectNode original, boolean comment) throws Exception {
            return reloadFromArcticShift(original);
        }
    }

    private static final class RefreshOption extends ReloadOption {
        RefreshOption(boolean comment) {
            super(110, comment ? "Refresh comment" : "Refresh post", android.R.drawable.ic_popup_sync,
                    "Refreshing from Reddit...", "Could not refresh from Reddit. Try again.");
        }

        @Override
        Reloaded load(ObjectNode original, boolean comment) throws Exception {
            return refreshFromReddit(original, comment);
        }
    }

    private static final class Reloaded {
        final ObjectNode data;
        final boolean bodyOnly;
        final String missing;

        Reloaded(ObjectNode data, boolean bodyOnly, String missing) {
            this.data = data;
            this.bodyOnly = bodyOnly;
            this.missing = missing;
        }
    }

    private static final class Binding {
        final ArchiveRetryItem item;
        final boolean first;
        final boolean second;
        final boolean third;
        final WeakReference<Object> glide;

        Binding(ArchiveRetryItem item, boolean first, boolean second, boolean third, Object glide) {
            this.item = item;
            this.first = first;
            this.second = second;
            this.third = third;
            this.glide = new WeakReference<>(glide);
        }
    }
}
