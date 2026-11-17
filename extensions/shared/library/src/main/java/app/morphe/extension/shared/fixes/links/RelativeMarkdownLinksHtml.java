/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.fixes.links;

import android.text.Html;
import android.text.Spanned;

import androidx.annotation.RequiresApi;

@SuppressWarnings("deprecation")
public final class RelativeMarkdownLinksHtml {
    private RelativeMarkdownLinksHtml() {}

    public static Spanned fromHtml(String text) {
        return Html.fromHtml(RelativeMarkdownLinks.repairHtml(text));
    }

    @RequiresApi(24)
    public static Spanned fromHtml(String text, int flags) {
        return Html.fromHtml(RelativeMarkdownLinks.repairHtml(text), flags);
    }

    public static Spanned fromHtml(String text, Html.ImageGetter images, Html.TagHandler tags) {
        return Html.fromHtml(RelativeMarkdownLinks.repairHtml(text), images, tags);
    }

    @RequiresApi(24)
    public static Spanned fromHtml(String text, int flags, Html.ImageGetter images, Html.TagHandler tags) {
        return Html.fromHtml(RelativeMarkdownLinks.repairHtml(text), flags, images, tags);
    }
}
