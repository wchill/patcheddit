/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.patcheddit.fixture;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public final class BoostCommentCollapseDeviceTest {
    private static final String PREFIX = "com.rubenmayayo.reddit.";
    private static final String PATCH = "morphe_patch_remember_collapsed_comments";
    private static final String FULL_COLLAPSE = "pref_comments_full_collapse";

    public static void run(Context context, Bundle arguments) throws Exception {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        Boolean previousPatch = preferences.contains(PATCH) ? preferences.getBoolean(PATCH, false) : null;
        Boolean previousFull = preferences.contains(FULL_COLLAPSE)
                ? preferences.getBoolean(FULL_COLLAPSE, false) : null;
        try {
            FutureTask<Void> test = new FutureTask<>(() -> {
                for (boolean full : new boolean[]{false, true}) {
                    check(preferences.edit().remove(PATCH).putBoolean(FULL_COLLAPSE, full).commit(),
                            "Could not set collapse preferences");
                    ThreadFixture thread = new ThreadFixture(context.getClassLoader());
                    thread.expectVisible("ABCDMEFG");

                    thread.collapse('C');
                    thread.expectVisible("ABCMEFG");
                    thread.collapse('B');
                    thread.expectVisible("ABEFG");
                    thread.collapse('A');
                    thread.expectVisible("AG");
                    thread.expand('A');
                    thread.expectVisible("ABEFG");
                    check(!thread.isExpanded('B'), "B should still be collapsed after opening A");
                    check((thread.viewType('B') == 6) == full, "B lost its native collapse style");

                    thread.expand('B');
                    thread.expectVisible("ABCMEFG");
                    check(!thread.isExpanded('C'), "C should still be collapsed after opening B");
                    thread.expand('C');
                    thread.expectVisible("ABCDMEFG");
                    thread.collapse('A');
                    thread.expand('A');
                    thread.expectVisible("ABCDMEFG");
                }

                check(preferences.edit().putBoolean(PATCH, false).commit(), "Could not disable the patch");
                ThreadFixture stock = new ThreadFixture(context.getClassLoader());
                stock.collapse('B');
                stock.collapse('A');
                stock.expand('A');
                stock.expectVisible("ABCDMEFG");
                check(stock.isExpanded('B'), "Disabling the patch did not restore stock expansion");
                return null;
            });
            PatchedditInstrumentation.instance.runOnMainSync(test);
            test.get(10, TimeUnit.SECONDS);
        } finally {
            SharedPreferences.Editor editor = preferences.edit();
            restore(editor, PATCH, previousPatch);
            restore(editor, FULL_COLLAPSE, previousFull);
            check(editor.commit(), "Could not restore collapse preferences");
        }
    }

    private static void restore(SharedPreferences.Editor editor, String name, Boolean value) {
        if (value == null) editor.remove(name);
        else editor.putBoolean(name, value);
    }

    private static final class ThreadFixture {
        private static final String LABELS = "ABCDMEFG";
        private final ArrayList<Object> rows = new ArrayList<>();
        private final Object fragment;
        private final Object adapter;
        private final Field expanded;
        private final Field replyCount;
        private final Method collapse;
        private final Method expand;
        private final Method viewType;

        ThreadFixture(ClassLoader loader) throws Exception {
            Class<?> fragmentType = loader.loadClass(PREFIX + "ui.comments.CommentsFragment");
            Class<?> adapterType = loader.loadClass(fragmentType.getName() + "$m");
            Class<?> comment = loader.loadClass(PREFIX + "models.reddit.CommentModel");
            Class<?> loadMore = loader.loadClass(PREFIX + "models.reddit.LoadMoreModel");
            fragment = fragmentType.getConstructor().newInstance();
            var adapterConstructor = adapterType.getDeclaredConstructor(fragmentType, loader.loadClass("u41"));
            adapterConstructor.setAccessible(true);
            adapter = adapterConstructor.newInstance(fragment, null);
            field(fragmentType, "B").set(fragment, adapter);
            field(fragmentType, "A").set(fragment, rows);
            field(fragmentType, "C").set(fragment, loader.loadClass(
                    PREFIX + "ui.customviews.ScrollingLinearLayoutManager").getConstructor().newInstance());

            Class<?> model = comment.getSuperclass();
            Field depth = field(model, "Q");
            replyCount = field(model, "S");
            expanded = field(model, "T");
            int[] depths = {0, 1, 2, 3, 2, 1, 2, 0};
            for (int i = 0; i < depths.length; i++) {
                Object row = (LABELS.charAt(i) == 'M' ? loadMore : comment).getConstructor().newInstance();
                depth.setInt(row, depths[i]);
                rows.add(row);
            }
            collapse = fragmentType.getMethod("i1", int.class);
            expand = fragmentType.getMethod("M", int.class);
            viewType = adapterType.getDeclaredMethod("getItemViewType", int.class);
            viewType.setAccessible(true);
        }

        void collapse(char label) throws Exception {
            int position = LABELS.indexOf(label);
            Object model = rows.get(position);
            // Run the real fragment callbacks, then apply the clicked row's ViewHolder update.
            replyCount.setInt(model, (Integer) collapse.invoke(fragment, position));
            expanded.setBoolean(model, false);
        }

        void expand(char label) throws Exception {
            int position = LABELS.indexOf(label);
            expand.invoke(fragment, position);
            expanded.setBoolean(rows.get(position), true);
        }

        boolean isExpanded(char label) throws Exception {
            return expanded.getBoolean(rows.get(LABELS.indexOf(label)));
        }

        int viewType(char label) throws Exception {
            return (Integer) viewType.invoke(adapter, LABELS.indexOf(label));
        }

        void expectVisible(String expected) throws Exception {
            StringBuilder actual = new StringBuilder();
            for (char label : LABELS.toCharArray()) {
                if (viewType(label) != 2) actual.append(label); // Boost's hidden placeholder view type.
            }
            check(expected.contentEquals(actual), "Expected visible rows " + expected + ", got " + actual);
        }

        private static Field field(Class<?> type, String name) throws Exception {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
