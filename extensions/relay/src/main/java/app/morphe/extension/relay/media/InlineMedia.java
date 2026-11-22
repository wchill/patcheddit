/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.relay.media;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import androidx.annotation.Keep;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.shared.settings.PatchSettings;
import reddit.news.oauth.glide.GlideImageSpan;
import reddit.news.oauth.glide.GlideImageSpanTarget;

public final class InlineMedia {
    private static final String KEEP = "morphe_relay_inline_keep_urls";
    private static final Pattern GIF = Pattern.compile("!\\[gif]\\(giphy\\|([A-Za-z0-9]{1,64})\\)");
    // Sessions and native targets hold only weak references to these view keys.
    private static final Map<TextView, Session> SESSIONS = new WeakHashMap<>();

    private InlineMedia() {}

    public static void setText(TextView view, CharSequence text) {
        Session previous = SESSIONS.remove(view);
        if (previous != null) previous.close();
        view.setText(text);
        if (!NativeInlineMedia.isIncluded() || !(text instanceof Spanned)) return;
        Session session = new Session(view, source((Spanned) text));
        if (session.images.isEmpty()) return;
        SESSIONS.put(view, session);
        view.addOnAttachStateChangeListener(session);
        session.render();
        if (view.isAttachedToWindow()) session.onViewAttachedToWindow(view);
    }

    private static boolean code(Spanned text, int start, int end) {
        for (TypefaceSpan font : text.getSpans(start, end, TypefaceSpan.class)) {
            if ("monospace".equals(font.getFamily())) return true;
        }
        return false;
    }

    private static Spanned source(Spanned original) {
        SpannableStringBuilder text = new SpannableStringBuilder(original);
        for (ImageSpan image : text.getSpans(0, text.length(), ImageSpan.class)) {
            int start = text.getSpanStart(image), end = text.getSpanEnd(image);
            if (MediaLinks.inlineImage(image.getSource()) != null
                    && text.getSpans(start, end, URLSpan.class).length == 0) {
                text.setSpan(new URLSpan(image.getSource()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        Matcher gif = GIF.matcher(text);
        while (gif.find()) {
            if (!code(text, gif.start(), gif.end())
                    && text.getSpans(gif.start(), gif.end(), URLSpan.class).length == 0) {
                text.setSpan(new URLSpan("https://media.giphy.com/media/" + gif.group(1) + "/giphy.gif"),
                        gif.start(), gif.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return text;
    }

    private static final class Session implements View.OnAttachStateChangeListener, ViewTreeObserver.OnPreDrawListener {
        final WeakReference<TextView> owner;
        final Spanned source;
        final List<Preview> images = new ArrayList<>();
        final int maxHeight;
        CharSequence rendered;
        ViewTreeObserver observer;
        boolean closed, rendering, keep, enabled = true;
        int width;

        Session(TextView view, Spanned source) {
            owner = new WeakReference<>(view);
            this.source = source;
            maxHeight = Math.round(320 * view.getResources().getDisplayMetrics().density);
            URLSpan[] links = source.getSpans(0, source.length(), URLSpan.class);
            Arrays.sort(links, (left, right) -> Integer.compare(source.getSpanStart(left), source.getSpanStart(right)));
            int previousEnd = -1;
            for (URLSpan link : links) {
                int start = source.getSpanStart(link), end = source.getSpanEnd(link);
                String image = MediaLinks.inlineImage(link.getURL());
                if (image != null && start >= previousEnd && end > start && !code(source, start, end)) {
                    images.add(new Preview(this, start, end, link.getURL(), image));
                    previousEnd = end;
                }
            }
        }

        boolean current() {
            TextView view = owner.get();
            return !closed && view != null && SESSIONS.get(view) == this && view.getText() == rendered;
        }

        void render() {
            TextView view = owner.get();
            if (closed || view == null || rendering) return;
            rendering = true;
            keep = PatchSettings.isEnabled(KEEP, true);
            // Native spans remain authoritative across option changes and reloads.
            SpannableStringBuilder result = new SpannableStringBuilder();
            int cursor = 0;
            for (Preview image : images) {
                if (!enabled || image.failed) continue;
                String label = source.subSequence(image.start, image.end).toString();
                boolean marker = label.startsWith("![gif](") || label.equals("\uFFFC");
                boolean hide = marker || (!keep && label.equals(image.link));
                result.append(source, cursor, hide ? image.start : image.end);
                if (result.length() > 0 && result.charAt(result.length() - 1) != '\n') result.append('\n');
                int position = result.length();
                result.append("\uFFFC");
                result.setSpan(image, position, position + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                result.setSpan(new URLSpan(image.url), position, position + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                result.append('\n');
                cursor = image.end;
            }
            result.append(source, cursor, source.length());
            view.setText(result, TextView.BufferType.SPANNABLE);
            rendered = view.getText();
            rendering = false;
        }

        @Override public void onViewAttachedToWindow(View view) {
            observer = view.getViewTreeObserver();
            observer.addOnPreDrawListener(this);
        }

        @Override public void onViewDetachedFromWindow(View view) {
            if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(this);
            observer = null;
            for (Preview image : images) image.clear();
        }

        @Override public boolean onPreDraw() {
            TextView view = owner.get();
            if (!current()) { close(); return true; }
            int available = view.getWidth() - view.getTotalPaddingLeft() - view.getTotalPaddingRight();
            boolean included = NativeInlineMedia.isIncluded();
            if (available != width || keep != PatchSettings.isEnabled(KEEP, true) || included != enabled) {
                width = Math.max(1, available);
                enabled = included;
                render();
            }
            Layout layout = view.getLayout();
            Rect visible = new Rect();
            boolean shown = enabled && view.getGlobalVisibleRect(visible) && layout != null;
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            for (Preview image : images) {
                int position = ((Spanned) rendered).getSpanStart(image);
                int top = position < 0 || layout == null ? 0 : location[1] + view.getTotalPaddingTop()
                        - view.getScrollY() + layout.getLineTop(layout.getLineForOffset(position));
                boolean load = shown && position >= 0 && top < visible.bottom + maxHeight
                        && top + image.height() > visible.top - maxHeight;
                if (load && !image.active && !image.failed) image.load(view);
                else if (!load && image.active) image.clear();
            }
            return true;
        }

        void close() {
            if (closed) return;
            closed = true;
            TextView view = owner.get();
            if (view != null) {
                view.removeOnAttachStateChangeListener(this);
                onViewDetachedFromWindow(view);
                if (SESSIONS.get(view) == this) SESSIONS.remove(view);
            }
        }
    }

    @Keep
    public static final class Preview extends GlideImageSpan implements Drawable.Callback {
        final Session session;
        final int start, end;
        final String link, url;
        Drawable drawable;
        PreviewTarget target;
        boolean active, failed;
        int ratioWidth = 2, ratioHeight = 1;

        Preview(Session session, int start, int end, String link, String url) {
            this.session = session; this.start = start; this.end = end; this.link = link; this.url = url;
        }

        int width() {
            int margin = 0;
            for (LeadingMarginSpan span : session.source.getSpans(start, end, LeadingMarginSpan.class)) {
                margin += span.getLeadingMargin(true);
            }
            return Math.max(1, Math.min(session.width - margin, session.maxHeight * ratioWidth / ratioHeight));
        }
        int height() { return Math.max(1, Math.min(session.maxHeight, width() * ratioHeight / ratioWidth)); }

        void load(TextView view) {
            active = true;
            target = new PreviewTarget(view, this);
            NativeInlineMedia.load(view, url, target, width(), session.maxHeight);
        }
        void clear() {
            active = false;
            TextView view = session.owner.get();
            if (target != null && view != null) NativeInlineMedia.clear(view, target);
            target = null;
            drop();
        }
        void drop() {
            if (drawable instanceof Animatable) ((Animatable) drawable).stop();
            if (drawable != null) drawable.setCallback(null);
            drawable = null;
        }
        // Native GlideImageSpan callbacks: cleared and resource ready.
        @Override public void a() { drop(); }
        @Override public void b(Drawable value) {
            if (!active || !session.current()) {
                if (value instanceof Animatable) ((Animatable) value).stop();
                return;
            }
            drop();
            drawable = value;
            ratioWidth = Math.max(1, value.getIntrinsicWidth());
            ratioHeight = Math.max(1, value.getIntrinsicHeight());
            value.setCallback(this);
            if (value instanceof Animatable) ((Animatable) value).start();
            session.render();
        }
        @Override public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt metrics) {
            if (metrics != null) {
                metrics.top = metrics.ascent = -height();
                metrics.bottom = metrics.descent = 0;
            }
            return width();
        }
        @Override public void draw(Canvas canvas, CharSequence text, int start, int end,
                                   float x, int top, int y, int bottom, Paint paint) {
            int save = canvas.save();
            canvas.translate(x, y - height());
            if (drawable != null) {
                drawable.setBounds(0, 0, width(), height());
                drawable.draw(canvas);
            } else {
                int color = paint.getColor();
                paint.setColor(0x22808080);
                canvas.drawRect(0, 0, width(), height(), paint);
                paint.setColor(color);
            }
            canvas.restoreToCount(save);
        }
        @Override public void invalidateDrawable(Drawable who) {
            TextView view = session.owner.get();
            if (active && view != null) view.postInvalidateOnAnimation();
        }
        @Override public void scheduleDrawable(Drawable who, Runnable what, long when) {
            TextView view = session.owner.get();
            if (active && view != null) view.postDelayed(what, Math.max(0, when - SystemClock.uptimeMillis()));
        }
        @Override public void unscheduleDrawable(Drawable who, Runnable what) {
            TextView view = session.owner.get();
            if (view != null) view.removeCallbacks(what);
        }
    }

    @Keep
    public static final class PreviewTarget extends GlideImageSpanTarget {
        final Preview preview;
        PreviewTarget(TextView view, Preview preview) { super(view, preview); this.preview = preview; }
        @Override public void failed(Drawable error) {
            if (!preview.active || !preview.session.current()) return;
            preview.failed = true;
            Log.w("RelayInlineMedia", "Could not load inline media");
            preview.session.render();
        }
    }
}
