/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.redditisfun.media;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.ReplacementSpan;
import android.text.style.URLSpan;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import app.morphe.extension.shared.settings.PatchSettings;

public final class InlineMedia {
    static final String KEY = "morphe_patch_inline_media";
    private static final WeakHashMap<TextView, Binding> BINDINGS = new WeakHashMap<>();

    private InlineMedia() {}

    static boolean option(String key, boolean fallback) {
        return PatchSettings.isEnabled("morphe_inline_" + key, fallback);
    }

    static boolean enabled() { return PatchSettings.isEnabled(KEY, true); }
    static boolean videos() { return enabled() && option("videos", true); }

    private static int percent(TextView view, String name) {
        int value = PatchSettings.preferences(view.getContext()).getInt("morphe_inline_" + name, 100);
        return Math.max(10, Math.min(100, value / 5 * 5));
    }

    /** Called only at RIF's body bind sites, after its normal rich-text rendering. */
    public static void setText(TextView view, CharSequence text) {
        Binding previous = BINDINGS.get(view);
        if (previous != null && enabled() && sameBody(previous.original, text)) {
            previous.original = text;
            for (Slot slot : previous.slots) if (slot.failed) slot.load();
            previous.render();
            return;
        }
        if (previous != null) previous.dispose();
        BINDINGS.remove(view);
        view.setText(text);
        if (!enabled() || !(text instanceof Spanned) || text.length() > 64 * 1024) return;
        Binding binding = new Binding(view, (Spanned) text);
        if (binding.slots.isEmpty()) return;
        BINDINGS.put(view, binding);
        view.addOnAttachStateChangeListener(binding);
        view.addOnLayoutChangeListener(binding);
        view.setOnTouchListener(binding);
        binding.render();
        if (view.isAttachedToWindow()) binding.onViewAttachedToWindow(view);
    }

    private static boolean sameBody(CharSequence left, CharSequence right) {
        if (!TextUtils.equals(left, right) || !(left instanceof Spanned) || !(right instanceof Spanned)) return false;
        Spanned a = (Spanned) left, b = (Spanned) right;
        URLSpan[] links = a.getSpans(0, a.length(), URLSpan.class), other = b.getSpans(0, b.length(), URLSpan.class);
        if (links.length != other.length) return false;
        for (int i = 0; i < links.length; i++) {
            if (!links[i].getURL().equals(other[i].getURL()) || a.getSpanStart(links[i]) != b.getSpanStart(other[i])
                    || a.getSpanEnd(links[i]) != b.getSpanEnd(other[i])) return false;
        }
        return true;
    }

    static final class Binding implements View.OnAttachStateChangeListener, View.OnLayoutChangeListener,
            ViewTreeObserver.OnPreDrawListener, ViewTreeObserver.OnWindowFocusChangeListener, View.OnTouchListener {
        final WeakReference<TextView> owner;
        final List<Slot> slots = new ArrayList<>();
        CharSequence original;
        boolean disposed;
        int width;
        private Slot pressed;
        private float downX, downY;
        private boolean cancelled, held, outsideMedia;
        private final Runnable hold = this::select;

        private void select() {
            TextView view = owner.get();
            if (pressed == null || cancelled || view == null || !current()) return;
            held = true;
            // Use the row's native selection action, not Android's text-selection action.
            if (!view.performClick()) {
                for (android.view.ViewParent parent = view.getParent(); parent instanceof View; parent = parent.getParent()) {
                    if (((View) parent).performClick()) break;
                }
            }
        }

        Binding(TextView view, Spanned source) {
            owner = new WeakReference<>(view);
            original = source;
            URLSpan[] links = source.getSpans(0, source.length(), URLSpan.class);
            Arrays.sort(links, (left, right) -> Integer.compare(source.getSpanStart(left), source.getSpanStart(right)));
            int end = -1;
            for (URLSpan link : links) {
                int start = source.getSpanStart(link), next = source.getSpanEnd(link);
                if (start < end || next <= start || !MediaLoader.supported(link.getURL(), videos())) continue;
                String normalized = MediaLoader.normalize(link.getURL());
                if (!videos() && isVideo(normalized)) continue;
                slots.add(new Slot(this, link, start, next));
                end = next;
                if (slots.size() == 16) break;
            }
        }

        boolean current() {
            TextView view = owner.get();
            return !disposed && view != null && BINDINGS.get(view) == this;
        }

        void render() {
            TextView view = owner.get();
            if (view == null || disposed) return;
            SpannableStringBuilder text = new SpannableStringBuilder(original);
            boolean keepUrls = option("keep_urls", true);
            for (int i = slots.size() - 1; i >= 0; i--) {
                Slot slot = slots.get(i);
                if (slot.failed) continue;
                String label = original.subSequence(slot.start, slot.end).toString();
                // Descriptive labels stay; GIF markers remain replaced by their media.
                boolean hideLink = label.equalsIgnoreCase("gif") || (!keepUrls && label.equals(slot.link.getURL()));
                int start = hideLink ? slot.start : slot.end;
                int end = slot.end;
                if (hideLink) text.removeSpan(slot.link);
                String prefix = start > 0 && text.charAt(start - 1) != '\n' ? "\n" : "";
                String suffix = end < text.length() && text.charAt(end) != '\n' ? "\n" : "";
                text.replace(start, end, prefix + "\ufffc" + suffix);
                int marker = start + prefix.length();
                text.setSpan(slot, marker, marker + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(new ClickableSpan() {
                    @Override public void onClick(@NonNull View widget) { slot.open(); }
                }, marker, marker + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (Build.VERSION.SDK_INT >= 30) slot.setContentDescription(slot.isVideo() ? "Inline video"
                        : slot.album != null && slot.album.items.size() > 1
                        ? "Image " + (slot.index + 1) + " of " + slot.album.items.size() : "Inline image");
            }
            view.setText(text, TextView.BufferType.SPANNABLE);
            view.requestLayout();
        }

        @Override public void onViewAttachedToWindow(View view) {
            view.getViewTreeObserver().addOnPreDrawListener(this);
            view.getViewTreeObserver().addOnWindowFocusChangeListener(this);
            for (Slot slot : slots) slot.load();
        }

        @Override public void onViewDetachedFromWindow(View view) {
            cancelHold();
            pressed = null;
            if (view.getViewTreeObserver().isAlive()) {
                view.getViewTreeObserver().removeOnPreDrawListener(this);
                view.getViewTreeObserver().removeOnWindowFocusChangeListener(this);
            }
            for (Slot slot : slots) slot.suspend();
        }

        @Override public void onWindowFocusChanged(boolean focused) {
            if (focused) return;
            cancelHold();
            for (Slot slot : slots) {
                slot.stopAnimation();
                if (slot.video != null) slot.video.visible(false);
            }
        }

        @Override public void onLayoutChange(View view, int left, int top, int right, int bottom,
                                             int oldLeft, int oldTop, int oldRight, int oldBottom) {
            TextView text = (TextView) view;
            int available = right - left - text.getTotalPaddingLeft() - text.getTotalPaddingRight();
            if (available > 0 && width != available) {
                width = available;
                render();
            }
        }

        @Override public boolean onPreDraw() {
            TextView view = owner.get();
            if (view == null || !current()) return true;
            if (!enabled()) {
                CharSequence source = original;
                dispose();
                BINDINGS.remove(view);
                view.setText(source);
                return true;
            }
            Rect visible = new Rect();
            boolean shown = view.hasWindowFocus() && view.getWindowVisibility() == View.VISIBLE
                    && view.isShown() && view.getLocalVisibleRect(visible);
            for (Slot slot : slots) {
                if (shown && slot.failed && SystemClock.elapsedRealtime() - slot.failedAt >= 120_000) {
                    slot.load();
                    render();
                }
                boolean active = shown && slot.bounds(view) && RectF.intersects(slot.box, new RectF(visible));
                if (slot.decoded != null && slot.decoded.drawable instanceof Animatable) {
                    Animatable animation = (Animatable) slot.decoded.drawable;
                    if (active && !animation.isRunning()) animation.start();
                    if (!active) slot.stopAnimation();
                }
                if (slot.video != null) slot.video.visible(active && videos());
                else if (active && slot.isVideo() && videos() && slot.album != null && slot.album.items.size() == 1) {
                    slot.video = new InlineVideo(slot);
                    slot.video.visible(true);
                }
            }
            return true;
        }

        @Override public boolean onTouch(View ignored, MotionEvent event) {
            TextView view = owner.get();
            if (view == null || !current()) return false;
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                view.removeCallbacks(hold);
                pressed = null;
                outsideMedia = false;
                for (Slot slot : slots) {
                    if (!slot.failed && slot.bounds(view) && slot.box.contains(event.getX(), event.getY())) {
                        pressed = slot;
                        break;
                    }
                }
                if (pressed == null) {
                    if (view.getLayout() != null && view.getText() instanceof Spanned) {
                        Spanned text = (Spanned) view.getText();
                        int line = view.getLayout().getLineForVertical((int) event.getY()
                                - view.getTotalPaddingTop() + view.getScrollY());
                        for (Slot slot : slots) {
                            int offset = text.getSpanStart(slot);
                            if (offset >= 0 && view.getLayout().getLineForOffset(offset) == line) outsideMedia = true;
                        }
                    }
                    return outsideMedia;
                }
                downX = event.getX();
                downY = event.getY();
                held = cancelled = false;
                if (option("long_press", true)) {
                    int delay = PatchSettings.preferences(view.getContext()).getInt("morphe_inline_hold_delay", 250);
                    view.postDelayed(hold, Math.max(100, Math.min(1000, delay / 50 * 50)));
                }
            } else if (pressed == null) {
                if (!outsideMedia) return false;
                if (action == MotionEvent.ACTION_UP) { outsideMedia = false; view.performClick(); }
                else if (action == MotionEvent.ACTION_CANCEL) outsideMedia = false;
                return true;
            }
            else if (action == MotionEvent.ACTION_MOVE) {
                int slop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
                if (Math.hypot(event.getX() - downX, event.getY() - downY) > slop) cancelHold();
            } else if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_DOWN) {
                cancelHold();
            } else if (action == MotionEvent.ACTION_UP) {
                view.removeCallbacks(hold);
                Slot target = pressed;
                pressed = null;
                if (!held && !cancelled && target.box.contains(event.getX(), event.getY())) {
                    target.tap((event.getX() - target.box.left) / target.box.width());
                }
            }
            return true;
        }

        private void cancelHold() {
            cancelled = true;
            TextView view = owner.get();
            if (view != null) view.removeCallbacks(hold);
        }

        void dispose() {
            TextView view = owner.get();
            if (view != null) {
                onViewDetachedFromWindow(view);
                view.removeOnAttachStateChangeListener(this);
                view.removeOnLayoutChangeListener(this);
                view.setOnTouchListener(null);
            }
            disposed = true;
        }
    }

    static final class Slot extends ReplacementSpan implements Drawable.Callback {
        final Binding binding;
        final URLSpan link;
        final int start, end;
        final RectF box = new RectF();
        MediaLoader.Album album;
        MediaLoader.Decoded decoded;
        InlineVideo video;
        int index, generation;
        boolean failed, loading;
        long failedAt;
        private final Set<Runnable> callbacks = new HashSet<>();
        private float aspect;
        private int displayWidth, displayHeight, nativeWidth;

        Slot(Binding binding, URLSpan link, int start, int end) {
            this.binding = binding; this.link = link; this.start = start; this.end = end;
        }

        boolean isVideo() {
            return album == null ? InlineMedia.isVideo(MediaLoader.normalize(link.getURL()))
                    : album.items.size() == 1 && album.items.get(0).video != null;
        }

        void load() {
            TextView view = binding.owner.get();
            if (failed && SystemClock.elapsedRealtime() - failedAt >= 120_000) {
                failed = false;
                if (video != null) { video.release(); video = null; }
            }
            if (loading || failed || !binding.current() || view == null || !view.isAttachedToWindow() || !enabled()) return;
            loading = true;
            int request = ++generation;
            MediaLoader.resolve(view.getContext().getApplicationContext(), link.getURL(), result -> {
                if (!valid(request)) return;
                if (result == null || (result.items.size() == 1 && result.items.get(0).video != null && !videos())) {
                    loading = false;
                    fail();
                    binding.render();
                    return;
                }
                album = result;
                image(index, request);
            });
        }

        private boolean valid(int request) {
            TextView view = binding.owner.get();
            return request == generation && binding.current() && enabled() && view != null && view.isAttachedToWindow();
        }

        private void image(int next, int request) {
            TextView view = binding.owner.get();
            if (view == null) return;
            MediaLoader.decode(view.getContext().getApplicationContext(), album.items.get(next), result -> {
                if (!valid(request)) { if (result != null) result.close(); return; }
                loading = false;
                if (result == null) {
                    if (decoded == null) {
                        if (!isVideo()) fail();
                        binding.render();
                    }
                    return;
                }
                if (decoded != null) decoded.close();
                decoded = result;
                index = next;
                result.drawable.setCallback(this);
                if (aspect == 0) {
                    aspect = (float) result.drawable.getIntrinsicWidth() / result.drawable.getIntrinsicHeight();
                    nativeWidth = result.nativeWidth;
                }
                MediaLoader.Item item = album.items.get(next);
                if (isVideo() && item.width > 0 && item.height > 0) aspect = (float) item.width / item.height;
                binding.render();
            });
        }

        void fail() { failed = true; failedAt = SystemClock.elapsedRealtime(); }

        void tap(float fraction) {
            if (video != null && album.items.size() == 1) { video.tap(); return; }
            if (album != null && album.items.size() > 1 && option("album_navigation", true)
                    && (fraction < 1f / 3 || fraction > 2f / 3)) {
                if (!loading) {
                    loading = true;
                    image((index + (fraction < .5f ? album.items.size() - 1 : 1)) % album.items.size(), ++generation);
                }
            } else open();
        }

        void open() {
            TextView view = binding.owner.get();
            if (view != null && binding.current()) link.onClick(view);
        }

        void suspend() {
            generation++;
            loading = false;
            stopAnimation();
            if (decoded != null) { decoded.close(); decoded = null; }
            if (video != null) { video.release(); video = null; }
        }

        void stopAnimation() {
            if (decoded != null && decoded.drawable instanceof Animatable) ((Animatable) decoded.drawable).stop();
            TextView view = binding.owner.get();
            if (view != null) for (Runnable callback : callbacks) view.removeCallbacks(callback);
            callbacks.clear();
        }

        @Override public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt metrics) {
            TextView view = binding.owner.get();
            if (view == null) return 0;
            DisplayMetrics screen = view.getResources().getDisplayMetrics();
            int nominal = Math.max(1, screen.widthPixels - Math.round(24 * screen.density));
            int width = Math.min(nominal, binding.width > 0 ? binding.width : nominal);
            int height = screen.widthPixels * 2;
            if (option("limit_width", false)) width = Math.min(width, nominal * percent(view, "max_width") / 100);
            if (option("limit_height", false)) height = Math.min(height, screen.heightPixels * percent(view, "max_height") / 100);
            if (isVideo()) height = Math.min(height, screen.heightPixels * 9 / 10);
            float ratio = aspect > 0 ? aspect : 2;
            if (!option("scale", true) && nativeWidth > 0) width = Math.min(width, nativeWidth);
            displayWidth = Math.max(1, Math.min(width, (int) (height * ratio)));
            displayHeight = Math.max(1, (int) (displayWidth / ratio));
            if (metrics != null) {
                metrics.ascent = metrics.top = -displayHeight - Math.round(6 * screen.density);
                metrics.descent = metrics.bottom = 0;
            }
            return displayWidth;
        }

        boolean bounds(TextView view) {
            Layout layout = view.getLayout();
            if (layout == null || !(view.getText() instanceof Spanned)) return false;
            Spanned text = (Spanned) view.getText();
            int offset = text.getSpanStart(this);
            if (offset < 0) return false;
            int line = layout.getLineForOffset(offset);
            if (line >= view.getMaxLines() || (layout.getEllipsisCount(line) > 0
                    && offset >= layout.getLineStart(line) + layout.getEllipsisStart(line))) return false;
            float x = layout.getPrimaryHorizontal(offset) + view.getTotalPaddingLeft() - view.getScrollX();
            float y = layout.getLineBaseline(line) + view.getTotalPaddingTop() - view.getScrollY();
            box.set(x, y - displayHeight, x + displayWidth, y);
            return true;
        }

        @Override public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x,
                                   int top, int y, int bottom, @NonNull Paint paint) {
            RectF area = new RectF(x, y - displayHeight, x + displayWidth, y);
            int saved = canvas.save();
            canvas.clipRect(area);
            canvas.drawColor(0x18000000);
            android.graphics.Bitmap frame = InlineVideo.frame(link.getURL());
            if (frame != null) {
                canvas.drawBitmap(frame, null, area, paint);
            } else if (decoded != null) {
                Drawable drawable = decoded.drawable;
                float scale = Math.min(area.width() / drawable.getIntrinsicWidth(), area.height() / drawable.getIntrinsicHeight());
                float width = drawable.getIntrinsicWidth() * scale, height = drawable.getIntrinsicHeight() * scale;
                canvas.translate(area.centerX() - width / 2, area.centerY() - height / 2);
                drawable.setBounds(0, 0, (int) width, (int) height);
                drawable.draw(canvas);
                canvas.restoreToCount(saved);
                saved = canvas.save();
                canvas.clipRect(area);
            }
            Paint label = new Paint(paint);
            label.setColor(0xffffffff);
            label.setShadowLayer(3, 0, 1, 0xff000000);
            label.setTextSize(Math.max(paint.getTextSize(), 16));
            if (album != null && album.items.size() > 1) {
                canvas.drawText((index + 1) + "/" + album.items.size(), x + 8, y - 8, label);
                if (option("album_navigation", true) && displayWidth > label.getTextSize() * 6) {
                    canvas.drawText("<", x + 8, area.centerY(), label);
                    canvas.drawText(">", area.right - label.getTextSize(), area.centerY(), label);
                }
            } else if (isVideo()) canvas.drawText("Play video", x + 8, y - 8, label);
            else if (decoded == null) {
                label.setColor(paint.getColor());
                canvas.drawText("Loading image...", x + 8, y - 8, label);
            }
            canvas.restoreToCount(saved);
        }

        @Override public void invalidateDrawable(@NonNull Drawable drawable) {
            TextView view = binding.owner.get();
            if (view != null && binding.current() && view.isAttachedToWindow()) view.invalidate();
        }
        @Override public void scheduleDrawable(@NonNull Drawable drawable, @NonNull Runnable action, long when) {
            TextView view = binding.owner.get();
            if (view != null && binding.current() && view.isAttachedToWindow() && view.hasWindowFocus()) {
                callbacks.add(action);
                view.postDelayed(action, Math.max(0, when - SystemClock.uptimeMillis()));
            }
        }
        @Override public void unscheduleDrawable(@NonNull Drawable drawable, @NonNull Runnable action) {
            TextView view = binding.owner.get();
            if (view != null) view.removeCallbacks(action);
            callbacks.remove(action);
        }
    }

    private static boolean isVideo(String url) {
        return url != null && (url.startsWith("https://v.redd.it/") || url.matches(".*\\.(?:mp4|gifv)(?:[?#].*)?")
                || app.morphe.extension.shared.fixes.media.CommentVideoLinks.baseUrl(url) != null);
    }
}
