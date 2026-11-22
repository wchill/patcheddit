/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.boost.media;

import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.text.style.ImageSpan;
import android.text.style.TypefaceSpan;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.widget.AppCompatImageView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.shared.settings.PatchSettings;

public final class InlineMedia {
    private static final String KEY = "morphe_patch_load_all_inline_media";
    private static final String TAG = "PatchedditInlineMedia";
    private static final Pattern GIF = Pattern.compile("!\\[gif]\\(giphy\\|([A-Za-z0-9]{1,64})\\)");

    public interface Failure { void onFailure(); }

    private InlineMedia() {}

    private static boolean enabled() { return PatchSettings.isEnabled(KEY, true); }

    public static boolean useNativePreview(boolean nativeEnabled) { return nativeEnabled && !enabled(); }

    /** Restore native text before Boost rebinds or clears the body. */
    public static void prepare(LinearLayout body) {
        if (body == null) return;
        for (int i = 0; i < body.getChildCount(); i++) {
            View view = body.getChildAt(i);
            if (view instanceof MediaImage) {
                ((MediaImage) view).session.close();
                return;
            }
        }
    }

    public static void bind(LinearLayout body, Object manager) {
        if (body == null || !enabled() || !MediaNativeBindings.inlineMediaEnabled()) return;
        Session session = null;
        List<View> children = new ArrayList<>();
        for (int i = 0; i < body.getChildCount(); i++) {
            View child = body.getChildAt(i);
            if (child instanceof MediaImage) return;
            children.add(child);
        }
        boolean keepUrls = PatchSettings.isEnabled("morphe_boost_inline_keep_urls", true);
        for (View child : children) {
            if (!(child instanceof TextView) || !(((TextView) child).getText() instanceof Spanned)) continue;
            TextView original = (TextView) child;
            Spanned nativeText = (Spanned) original.getText();
            Spanned source = inlineSource(nativeText);
            URLSpan[] links = source.getSpans(0, source.length(), URLSpan.class);
            Arrays.sort(links, (left, right) -> Integer.compare(source.getSpanStart(left), source.getSpanStart(right)));
            TextView part = original;
            int cursor = 0, position = body.indexOfChild(original) + 1;
            boolean split = false;
            for (URLSpan link : links) {
                String image = imageUrl(link.getURL());
                int start = source.getSpanStart(link), end = source.getSpanEnd(link);
                if (image == null || start < cursor || end <= start) continue;
                if (session == null) session = new Session(body, manager, keepUrls);
                if (!split) {
                    session.originals.add(new Original(original, nativeText));
                    split = true;
                } else {
                    part = MediaNativeBindings.inlineTextView(body);
                    session.add(part, position++);
                }
                CharSequence label = source.subSequence(start, end);
                boolean hide = label.toString().equalsIgnoreCase("gif") || label.toString().equals("\ufffc")
                        || (!keepUrls && label.toString().equals(link.getURL()));
                CharSequence fallback = new SpannableStringBuilder(source, cursor, end);
                setText(part, new SpannableStringBuilder(source, cursor, hide ? start : end));
                MediaImage preview = new MediaImage(session, part, fallback, image, link.getURL());
                session.images.add(preview);
                session.add(preview, position++);
                cursor = end;
            }
            if (split && cursor < source.length()) {
                TextView tail = MediaNativeBindings.inlineTextView(body);
                setText(tail, new SpannableStringBuilder(source, cursor, source.length()));
                session.add(tail, position);
            }
        }
        if (session != null) session.start();
    }

    // Embedded images and Reddit GIF markers share the ordinary link renderer.
    private static Spanned inlineSource(Spanned original) {
        SpannableStringBuilder text = new SpannableStringBuilder(original);
        for (ImageSpan image : text.getSpans(0, text.length(), ImageSpan.class)) {
            int start = text.getSpanStart(image), end = text.getSpanEnd(image);
            if (imageUrl(image.getSource()) != null && text.getSpans(start, end, URLSpan.class).length == 0) {
                text.setSpan(new URLSpan(image.getSource()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        List<int[]> markers = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        Matcher matches = GIF.matcher(text);
        while (matches.find()) {
            boolean code = false;
            for (TypefaceSpan font : text.getSpans(matches.start(), matches.end(), TypefaceSpan.class)) {
                if ("monospace".equals(font.getFamily())) code = true;
            }
            if (!code && text.getSpans(matches.start(), matches.end(), URLSpan.class).length == 0) {
                markers.add(new int[]{matches.start(), matches.end()});
                ids.add(matches.group(1));
            }
        }
        for (int i = markers.size() - 1; i >= 0; i--) {
            int[] marker = markers.get(i);
            text.replace(marker[0], marker[1], "gif");
            text.setSpan(new URLSpan("https://giphy.com/gifs/" + ids.get(i)),
                    marker[0], marker[0] + 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return text;
    }

    private static void setText(TextView view, CharSequence text) {
        view.setText(text, TextView.BufferType.SPANNABLE);
        view.setVisibility(TextUtils.getTrimmedLength(text) == 0 ? View.GONE : View.VISIBLE);
    }

    static String imageUrl(String value) {
        if (value == null || value.length() > 4096) return null;
        Uri url = Uri.parse(value);
        String scheme = url.getScheme(), host = url.getHost(), path = url.getPath();
        if (scheme == null || host == null || path == null || url.getEncodedAuthority().contains("@")
                || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) return null;
        host = host.toLowerCase(Locale.ROOT);
        if (host.equals("giphy.com") || host.endsWith(".giphy.com")) {
            List<String> segments = url.getPathSegments();
            if (segments.isEmpty()) return null;
            String id = segments.get(segments.size() - 1);
            if (segments.get(0).equals("media") && segments.size() >= 2) {
                id = segments.get(Math.max(1, segments.size() - 2));
            } else if (host.equals("i.giphy.com") && id.endsWith(".gif")) {
                id = id.substring(0, id.length() - 4);
            } else if (id.contains("-")) id = id.substring(id.lastIndexOf('-') + 1);
            return id.matches("[A-Za-z0-9]{1,64}") ? "https://media.giphy.com/media/" + id
                    + (Build.VERSION.SDK_INT >= 28 ? "/200.webp" : "/200.gif") : null;
        }
        return host.equals("i.redd.it") || host.equals("preview.redd.it") || host.equals("external-preview.redd.it")
                || path.toLowerCase(Locale.ROOT).matches(".*\\.(?:jpe?g|png|gif|webp)") ? value : null;
    }

    private static final class Original {
        final TextView view;
        final CharSequence text;
        final int visibility;
        Original(TextView view, CharSequence text) {
            this.view = view; this.text = text; visibility = view.getVisibility();
        }
    }

    private static final class Session implements View.OnAttachStateChangeListener, ViewTreeObserver.OnPreDrawListener {
        final LinearLayout body;
        final Object manager;
        final boolean keepUrls;
        final List<Original> originals = new ArrayList<>();
        final List<View> added = new ArrayList<>();
        final List<MediaImage> images = new ArrayList<>();
        ViewTreeObserver observer;
        boolean closed;

        Session(LinearLayout body, Object manager, boolean keepUrls) {
            this.body = body; this.manager = MediaNativeBindings.inlineManager(manager, body.getContext());
            this.keepUrls = keepUrls;
        }

        void add(View view, int position) {
            body.addView(view, position, new LinearLayout.LayoutParams(-1, -2));
            added.add(view);
        }

        void start() {
            body.addOnAttachStateChangeListener(this);
            if (body.isAttachedToWindow()) onViewAttachedToWindow(body);
        }

        @Override public void onViewAttachedToWindow(View view) {
            observer = body.getViewTreeObserver();
            observer.addOnPreDrawListener(this);
        }

        @Override public void onViewDetachedFromWindow(View view) { stop(); }

        private void stop() {
            if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(this);
            observer = null;
            for (MediaImage image : images) image.clear();
        }

        @Override public boolean onPreDraw() {
            if (!enabled() || !MediaNativeBindings.inlineMediaEnabled()) {
                close();
                return false;
            }
            if (keepUrls != PatchSettings.isEnabled("morphe_boost_inline_keep_urls", true)) {
                close();
                bind(body, manager);
                return false;
            }
            for (MediaImage image : images) {
                android.graphics.Rect visible = new android.graphics.Rect();
                boolean shown = !closed && body.hasWindowFocus() && body.isShown()
                        && image.getLocalVisibleRect(visible);
                if (shown && !image.active && !image.failed) image.load();
                else if (!shown && image.active) image.clear();
            }
            return true;
        }

        void close() {
            if (closed) return;
            closed = true;
            stop();
            body.removeOnAttachStateChangeListener(this);
            for (Original original : originals) {
                original.view.setText(original.text, TextView.BufferType.SPANNABLE);
                original.view.setVisibility(original.visibility);
            }
            for (View view : added) if (view.getParent() == body) body.removeView(view);
        }
    }

    private static final class MediaImage extends AppCompatImageView {
        final Session session;
        final TextView prefix;
        final CharSequence fallback;
        final String image, link;
        boolean active, failed;
        float aspect;

        MediaImage(Session session, TextView prefix, CharSequence fallback, String image, String link) {
            super(session.body.getContext(), null);
            this.session = session; this.prefix = prefix; this.fallback = fallback; this.image = image; this.link = link;
            setScaleType(ScaleType.FIT_CENTER);
            setContentDescription("Inline image");
            int gap = Math.round(6 * getResources().getDisplayMetrics().density);
            setPadding(prefix.getPaddingLeft(), gap, prefix.getPaddingRight(), gap);
            setOnClickListener(view -> MediaNativeBindings.openInlineUrl(getContext(), link));
            setOnLongClickListener(view -> prefix.performLongClick());
        }

        @Override public void setImageDrawable(Drawable drawable) {
            super.setImageDrawable(drawable);
            if (drawable != null && drawable.getIntrinsicWidth() > 0 && drawable.getIntrinsicHeight() > 0) {
                aspect = (float) drawable.getIntrinsicWidth() / drawable.getIntrinsicHeight();
                requestLayout();
            }
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int available = Math.max(1, width - getPaddingLeft() - getPaddingRight());
            int height = aspect > 0 ? Math.min(getResources().getDisplayMetrics().heightPixels / 2,
                    Math.round(available / aspect)) : Math.round(64 * getResources().getDisplayMetrics().density);
            setMeasuredDimension(width, resolveSize(height + getPaddingTop() + getPaddingBottom(), heightSpec));
        }

        void load() {
            active = true;
            try {
                // Native Glide handles caching, decoding, animation, and request cancellation.
                MediaNativeBindings.loadInlineImage(session.manager, getContext(), image, this,
                        Math.min(1024, Math.max(1, getWidth())), 1024, this::fail);
            } catch (IllegalArgumentException | IllegalStateException exception) { fail(); }
        }

        void fail() {
            if (!active || session.closed) return;
            active = false;
            failed = true;
            Log.w(TAG, "Inline image could not load; the original link remains available");
            setText(prefix, fallback);
            setVisibility(GONE);
        }

        void clear() {
            active = false;
            MediaNativeBindings.clearInlineImage(session.manager, getContext(), this);
        }
    }
}
