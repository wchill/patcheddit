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
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.util.Log;
import android.util.LruCache;
import android.view.MotionEvent;
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
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long HIDDEN_RELEASE_MS = 250;
    private static final LruCache<String, Aspect> ASPECTS = new LruCache<>(256);
    // Sessions and native targets hold only weak references to these view keys.
    private static final Map<TextView, Session> SESSIONS = new WeakHashMap<>();

    private InlineMedia() {}

    private record Aspect(int width, int height) {}

    @Keep
    public static final class PreviewLink extends URLSpan {
        PreviewLink(String url) { super(url); }
    }

    public static boolean outsidePreviewTarget(TextView view, MotionEvent event) {
        Session session = SESSIONS.get(view);
        Layout layout = view.getLayout();
        if (session == null || !session.current() || !session.enabled || layout == null) return false;
        float x = event.getX() - view.getTotalPaddingLeft() + view.getScrollX();
        float y = event.getY() - view.getTotalPaddingTop() + view.getScrollY();
        int line = layout.getLineForVertical((int) y);
        Spanned text = (Spanned) session.rendered;
        int start = layout.getLineStart(line), end = layout.getLineEnd(line);
        for (Preview preview : text.getSpans(start, end, Preview.class)) {
            int position = text.getSpanStart(preview);
            if (position < start || position >= end) continue;
            float localX = x - layout.getLineLeft(line);
            int height = preview.height(), width = preview.displayWidth();
            float localY = y - (layout.getLineBaseline(line) - height);
            // Reserve the outside of the preview for the comment, not the image viewer.
            return preview.drawable == null || localX < width * 0.25f || localX >= width * 0.75f
                    || localY < height * 0.25f || localY >= height * 0.75f;
        }
        return false;
    }

    public static void setText(TextView view, CharSequence text) {
        boolean enabled = NativeInlineMedia.isIncluded();
        Session previous = SESSIONS.get(view);
        if (enabled && previous != null && previous.original == text && previous.current()) return;
        if (previous != null) previous.close();
        if (enabled && text instanceof Spanned) {
            Session session = new Session(view, (Spanned) text);
            if (!session.images.isEmpty()) {
                SESSIONS.put(view, session);
                view.addOnAttachStateChangeListener(session);
                session.render();
                if (view.isAttachedToWindow()) session.onViewAttachedToWindow(view);
                return;
            }
        }
        view.setText(text);
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
        final Spanned original, source;
        final List<Preview> images = new ArrayList<>();
        final int frameHeight;
        final Rect visible = new Rect();
        final Runnable release = this::releaseImages;
        final Runnable resize = this::applyMediaSizes;
        CharSequence rendered;
        ViewTreeObserver observer;
        boolean closed, rendering, keep, releasePending, resizePending, enabled = true;
        int width;

        Session(TextView view, Spanned original) {
            owner = new WeakReference<>(view);
            this.original = original;
            source = source(original);
            frameHeight = Math.round(320 * view.getResources().getDisplayMetrics().density);
            width = Math.max(1, availableWidth(view));
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

        private static int availableWidth(TextView view) {
            return view.getWidth() - view.getTotalPaddingLeft() - view.getTotalPaddingRight();
        }

        void render() {
            TextView view = owner.get();
            if (closed || view == null || rendering) return;
            rendering = true;
            keep = PatchSettings.isEnabled(KEEP, true);
            if (!enabled) {
                view.setText(original);
                rendered = view.getText();
                rendering = false;
                return;
            }
            SpannableStringBuilder result = new SpannableStringBuilder();
            int cursor = 0;
            for (Preview image : images) {
                String label = source.subSequence(image.start, image.end).toString();
                boolean marker = label.startsWith("![gif](") || label.equals("\uFFFC");
                boolean hide = marker || (!keep && label.equals(image.link));
                result.append(source, cursor, hide ? image.start : image.end);
                if (result.length() > 0 && result.charAt(result.length() - 1) != '\n') result.append('\n');
                int position = result.length();
                result.append(image.failed ? image.link : "\uFFFC");
                if (!image.failed) {
                    result.setSpan(image, position, result.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                result.setSpan(image.failed ? new URLSpan(image.url) : new PreviewLink(image.url),
                        position, result.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (image.end < source.length() && source.charAt(image.end) != '\n') result.append('\n');
                cursor = image.end;
            }
            result.append(source, cursor, source.length());
            view.setText(result, TextView.BufferType.SPANNABLE);
            rendered = view.getText();
            rendering = false;
        }

        void requestMediaResize() {
            TextView view = owner.get();
            if (view == null || resizePending) return;
            resizePending = true;
            view.postOnAnimation(resize);
        }

        private void applyMediaSizes() {
            resizePending = false;
            if (!current() || !enabled) return;
            boolean changed = false;
            for (Preview image : images) {
                if (image.failed) {
                    changed |= ((Spanned) rendered).getSpanStart(image) >= 0;
                } else if (image.aspect == null) {
                    int oldHeight = image.height();
                    image.aspect = ASPECTS.get(image.url);
                    changed |= image.height() != oldHeight;
                }
            }
            if (changed) render();
        }

        @Override public void onViewAttachedToWindow(View view) {
            cancelRelease();
            observer = view.getViewTreeObserver();
            observer.addOnPreDrawListener(this);
        }

        @Override public void onViewDetachedFromWindow(View view) {
            if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(this);
            observer = null;
            for (Preview image : images) image.setVisible(false);
            // ListView can detach and reattach the same row during a single layout.
            scheduleRelease();
        }

        private void scheduleRelease() {
            if (releasePending || images.stream().noneMatch(image -> image.active)) return;
            releasePending = true;
            MAIN.postDelayed(release, HIDDEN_RELEASE_MS);
        }

        private void cancelRelease() {
            if (!releasePending) return;
            MAIN.removeCallbacks(release);
            releasePending = false;
        }

        private void releaseImages() {
            releasePending = false;
            clearImages();
        }

        private void clearImages() {
            for (Preview image : images) image.clear();
        }

        @Override public boolean onPreDraw() {
            TextView view = owner.get();
            if (!current()) { close(); return true; }
            int available = Math.max(1, availableWidth(view));
            boolean included = NativeInlineMedia.isIncluded();
            if (available != width || keep != PatchSettings.isEnabled(KEEP, true) || included != enabled) {
                width = available;
                enabled = included;
                render();
            }
            if (!enabled) {
                cancelRelease();
                clearImages();
                return true;
            }
            Layout layout = view.getLayout();
            if (layout == null) return true;
            boolean shown = view.isShown() && view.getWindowVisibility() == View.VISIBLE
                    && view.getLocalVisibleRect(visible);
            if (!shown) {
                for (Preview image : images) image.setVisible(false);
                scheduleRelease();
                return true;
            }
            cancelRelease();
            for (Preview image : images) {
                int position = ((Spanned) rendered).getSpanStart(image);
                if (position < 0) continue;
                int bottom = view.getTotalPaddingTop()
                        + layout.getLineBaseline(layout.getLineForOffset(position));
                int top = bottom - image.height();
                image.setVisible(top < visible.bottom && bottom > visible.top);
                if (top < visible.bottom + frameHeight && bottom > visible.top - frameHeight) {
                    if (!image.active && !image.failed) image.load(view);
                } else if (top >= visible.bottom + 2 * frameHeight || bottom <= visible.top - 2 * frameHeight) {
                    if (image.active) image.clear();
                }
            }
            return true;
        }

        void close() {
            if (closed) return;
            closed = true;
            TextView view = owner.get();
            if (view != null) {
                view.removeOnAttachStateChangeListener(this);
                view.removeCallbacks(resize);
                if (SESSIONS.get(view) == this) SESSIONS.remove(view);
            }
            if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(this);
            observer = null;
            cancelRelease();
            clearImages();
        }
    }

    @Keep
    public static final class Preview extends GlideImageSpan implements Drawable.Callback {
        final Session session;
        final int start, end;
        final String link, url;
        Aspect aspect;
        Drawable drawable;
        PreviewTarget target;
        boolean active, failed, visible;

        Preview(Session session, int start, int end, String link, String url) {
            this.session = session; this.start = start; this.end = end; this.link = link; this.url = url;
            aspect = ASPECTS.get(url);
        }

        int width() {
            int margin = 0;
            for (LeadingMarginSpan span : session.source.getSpans(start, end, LeadingMarginSpan.class)) {
                margin += span.getLeadingMargin(true);
            }
            return Math.max(1, session.width - margin);
        }

        int height() {
            return aspect == null ? session.frameHeight : Math.max(1, Math.min(session.frameHeight,
                    Math.round(width() * ((float) aspect.height / aspect.width))));
        }

        int displayWidth() {
            return aspect == null ? width() : Math.max(1, Math.min(width(),
                    Math.round(height() * ((float) aspect.width / aspect.height))));
        }

        void load(TextView view) {
            active = true;
            target = new PreviewTarget(view, this);
            NativeInlineMedia.load(view, url, target, width(), session.frameHeight);
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
        void setVisible(boolean visible) {
            if (this.visible == visible) return;
            this.visible = visible;
            updateAnimation();
        }
        private void updateAnimation() {
            if (!(drawable instanceof Animatable)) return;
            Animatable animation = (Animatable) drawable;
            if (visible && !animation.isRunning()) animation.start();
            if (!visible && animation.isRunning()) animation.stop();
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
            value.setCallback(this);
            if (aspect == null && value.getIntrinsicWidth() > 0 && value.getIntrinsicHeight() > 0) {
                if (ASPECTS.get(url) == null) {
                    ASPECTS.put(url, new Aspect(value.getIntrinsicWidth(), value.getIntrinsicHeight()));
                }
                session.requestMediaResize();
            }
            updateAnimation();
            invalidateDrawable(value);
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
            int width = displayWidth(), height = height();
            canvas.translate(x, y - height);
            if (drawable != null && aspect != null) {
                drawable.setBounds(0, 0, width, height);
                drawable.draw(canvas);
            } else {
                int color = paint.getColor();
                paint.setColor(0x22808080);
                canvas.drawRect(0, 0, width, height, paint);
                paint.setColor(color);
            }
            canvas.restoreToCount(save);
        }
        @Override public void invalidateDrawable(Drawable who) {
            TextView view = session.owner.get();
            if (active && who == drawable && view != null) view.postInvalidateOnAnimation();
        }
        @Override public void scheduleDrawable(Drawable who, Runnable what, long when) {
            TextView view = session.owner.get();
            if (active && who == drawable && view != null) {
                view.postDelayed(what, Math.max(0, when - SystemClock.uptimeMillis()));
            }
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
            if (preview.target != this || !preview.active || !preview.session.current()) return;
            preview.failed = true;
            Log.w("RelayInlineMedia", "Could not load inline media");
            preview.session.requestMediaResize();
        }
    }
}
