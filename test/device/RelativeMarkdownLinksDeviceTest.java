/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.patcheddit.fixture;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public final class RelativeMarkdownLinksDeviceTest {
    private static final String KEY = "morphe_patch_fix_broken_markdown_links";
    private static final String USER = "[<b>Example member</b>](<a href=\"/u/example_member\">u/example_member</a>)";
    private static final String USER_FIXED = "<a href=\"https://www.reddit.com/u/example_member\"><b>Example member</b></a>";
    private static final String SUB = "[Example community](<a href=\"/r/example_sub\">r/example_sub</a>)";
    private static final String SUB_FIXED = "<a href=\"https://www.reddit.com/r/example_sub\">Example community</a>";

    public static void run(Context context, Bundle arguments) throws Exception {
        Class<?> settings = context.getClassLoader().loadClass("app.morphe.extension.shared.settings.PatchSettings");
        SharedPreferences preferences = (SharedPreferences) settings.getMethod("preferences", Context.class)
                .invoke(null, context);
        Object previous = preferences.getAll().get(KEY);
        FutureTask<Void> task = new FutureTask<>(() -> {
            try {
                for (String renderer : renderers(context)) {
                    compare(context, preferences, renderer, "<p><i>Before</i> " + USER + " " + SUB
                                    + " <a href=\"https://example.com/a_b\">working</a> after.</p>",
                            "<p><i>Before</i> " + USER_FIXED + " " + SUB_FIXED
                                    + " <a href=\"https://example.com/a_b\">working</a> after.</p>");
                    compare(context, preferences, renderer,
                            "[<a href=\"/u/other\">u/other</a> label](/<a href=\"/U/Example-Name/\">U/Example-Name</a>/)",
                            "<a href=\"https://www.reddit.com/u/Example-Name\">u/other label</a>");
                    for (String unchanged : new String[]{
                            "<pre><code>" + USER + "</code></pre>", "<tt>" + USER + "</tt>",
                            "<span style=\"font-family:monospace\">" + USER + "</span>",
                            "[<code>Example</code> member](<a href=\"/u/example_member\">u/example_member</a>)",
                            "[Example member](u/example_member)",
                            "<a href=\"/u/outside\">outside</a> [Example member](u/example_member)",
                            "[outer " + USER + "]", "[line<br>break](<a href=\"/u/member\">u/member</a>)",
                            "[]( <a href=\"/u/member\">u/member</a>)",
                            "[Example](/<a href=\"/u/member\">/u/member</a>)",
                            "[Example](<a href=\"/u/member\">u/member/</a>/)",
                            "[Example member](<a href=\"https://example.com/u/example_member\">u/example_member</a>)"}) {
                        compare(context, preferences, renderer, unchanged, unchanged);
                    }
                }
            } finally {
                SharedPreferences.Editor restore = preferences.edit();
                if (previous == null) restore.remove(KEY); else restore.putBoolean(KEY, (Boolean) previous);
                check(restore.commit(), "Could not restore original Markdown switch");
            }
            return null;
        });
        check(new Handler(Looper.getMainLooper()).post(task), "Could not schedule native renderer fixture");
        task.get(60, TimeUnit.SECONDS);
    }

    private static List<String> renderers(Context context) {
        String app = context.getPackageName();
        if (app.startsWith("com.andrewshu")) return java.util.Arrays.asList("rif-html", "rif-encoded");
        if (app.startsWith("o.o.joey")) return java.util.Arrays.asList("joey-primary", "joey-legacy");
        if (app.startsWith("com.laurencedawson")) return java.util.Arrays.asList("sync-primary", "sync-legacy");
        return Collections.singletonList(app);
    }

    private static void compare(Context context, SharedPreferences preferences, String renderer,
                                String input, String expected) throws Exception {
        set(preferences, false);
        Spanned nativeOriginal = render(context, renderer, input);
        Spanned nativeExpected = render(context, renderer, expected);
        set(preferences, true);
        Spanned actual = render(context, renderer, input);
        check(nativeExpected.toString().equals(actual.toString()), renderer + " rendered wrong text:\n" + actual);
        check(spans(nativeExpected).equals(spans(actual)), renderer + " changed native link/style spans:\n"
                + spans(nativeExpected) + "\n" + spans(actual));
        if (!input.equals(expected)) {
            check(!nativeOriginal.toString().equals(actual.toString()), renderer + " did not reproduce the original bug");
        }
        set(preferences, false);
        check(nativeOriginal.toString().equals(render(context, renderer, input).toString()),
                renderer + " did not restore native rendering after disabling the switch");
    }

    private static void set(SharedPreferences preferences, boolean enabled) {
        check(preferences.edit().putBoolean(KEY, enabled).commit(), "Could not change Markdown switch");
    }

    private static List<String> spans(Spanned text) {
        List<String> result = new ArrayList<>();
        for (Object span : text.getSpans(0, text.length(), Object.class)) {
            String value = "";
            if (span instanceof URLSpan) value = ((URLSpan) span).getURL();
            if (span instanceof StyleSpan) value = Integer.toString(((StyleSpan) span).getStyle());
            if (span instanceof TypefaceSpan) value = ((TypefaceSpan) span).getFamily();
            result.add(span.getClass().getName() + ":" + text.getSpanStart(span) + ":" + text.getSpanEnd(span)
                    + ":" + text.getSpanFlags(span) + ":" + value);
        }
        Collections.sort(result);
        return result;
    }

    private static Spanned render(Context context, String renderer, String html) throws Exception {
        ClassLoader loader = context.getClassLoader();
        String app = context.getPackageName();
        if (renderer.startsWith("rif-")) {
            Class<?> type = loader.loadClass(app.endsWith("donation") ? "p5.z" : "o5.z");
            if (renderer.equals("rif-encoded")) {
                String encoded = html.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                return (Spanned) type.getMethod("i", String.class).invoke(null, encoded);
            }
            return (Spanned) type.getMethod("l", String.class).invoke(null, html);
        }
        if (app.equals("com.rubenmayayo.reddit")) {
            Class<?> type = loader.loadClass("com.rubenmayayo.reddit.ui.customviews.LinkTextView");
            TextView view = (TextView) type.getConstructor(Context.class).newInstance(context);
            type.getMethod("setTextHtmlParsed", String.class).invoke(view, html);
            return (Spanned) view.getText();
        }
        if (app.startsWith("com.onelouder")) {
            Class<?> type = loader.loadClass("com.onelouder.baconreader.parser.RedditSpanner");
            Object parser = type.getConstructor(String.class, String.class).newInstance(html, "https://www.reddit.com");
            return (Spanned) type.getMethod("getSpannable").invoke(parser);
        }
        if (app.equals("free.reddit.news") || app.equals("reddit.news")) {
            Class<?> type = loader.loadClass("reddit.news.utils.RedditUtils");
            return (Spanned) type.getMethod("j", String.class, boolean.class, String.class)
                    .invoke(null, html, true, "");
        }
        if (renderer.equals("joey-legacy")) {
            Class<?> type = loader.loadClass("o.o.joey.CustomViews.HTMLTextView");
            return (Spanned) type.getMethod("c", String.class).invoke(null, html);
        }
        if (renderer.equals("joey-primary")) {
            Class<?> type = loader.loadClass("o.o.joey.CustomViews.HtmlDispaly");
            Object view = type.getConstructor(Context.class).newInstance(context);
            Class<?> textKind = loader.loadClass("o.o.joey.CustomViews.HtmlDispaly$g");
            type.getMethod("setTextHtml", String.class, textKind)
                    .invoke(view, html, textKind.getEnumConstants()[0]);
            android.text.SpannableStringBuilder result = new android.text.SpannableStringBuilder();
            collectText((android.view.View) view, result);
            return result;
        }
        if (renderer.equals("sync-legacy")) {
            return (Spanned) loader.loadClass("d7.f").getMethod("j", String.class).invoke(null, html);
        }
        if (renderer.equals("sync-primary")) {
            Class<?> builderType = loader.loadClass("oc.c");
            Class<?> optionsType = loader.loadClass("nc.a");
            Object builder = builderType.getConstructor().newInstance();
            Object options = optionsType.getConstructor().newInstance();
            loader.loadClass("nc.c").getMethod("a", builderType, optionsType, String.class)
                    .invoke(null, builder, options, html);
            return new android.text.SpannedString((CharSequence) builderType.getMethod("d").invoke(builder));
        }
        if (app.startsWith("ml.docilealligator.infinityforreddit")) {
            Class<?> type = loader.loadClass(app.endsWith("patreon") ? "x77" : "oe7");
            TextView view = new TextView(context);
            type.getMethod("r", TextView.class, String.class, boolean.class).invoke(null, view, html, false);
            return (Spanned) view.getText();
        }
        throw new AssertionError("Unsupported fixture app " + app);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void collectText(android.view.View view, android.text.SpannableStringBuilder result) {
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (text.length() != 0) result.append(text).append("\n");
        } else if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) collectText(group.getChildAt(index), result);
        }
    }
}
