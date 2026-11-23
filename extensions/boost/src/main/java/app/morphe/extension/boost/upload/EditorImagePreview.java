/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (82d70f7).
 */
package app.morphe.extension.boost.upload;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.boost.media.MediaNativeBindings;

/**
 * Live image preview for Boost editors using FormattingBar.setEditText.
 *
 * The source URL remains in the EditText because Reddit comments and self
 * posts require it in submitted Markdown. This class only adds a visual
 * preview directly above the active editor.
 */
public final class EditorImagePreview {
    public static final String CONTRACT_MARKER =
            "MORPHE_BOOST_EDITOR_IMAGE_PREVIEW_ISSUE66_V1";

    private static final String TAG = "MorpheEditorPreview";
    private static final String PREVIEW_TAG =
            "morphe_boost_editor_image_preview_issue66_v1";

    private static final int MAX_PREVIEWS = 4;
    private static final long UPDATE_DELAY_MS = 120L;

    private static final Pattern IMAGE_URL_PATTERN = Pattern.compile(
            "https?://(?:"
                    + "(?:i\\.redd\\.it|preview\\.redd\\.it|"
                    + "external-preview\\.redd\\.it)/[^\\s\\\"'<>]+"
                    + "|(?:i\\.ibb\\.co|image\\.ibb\\.co)/[^\\s\\\"'<>]+"
                    + "|i\\.imgur\\.com/[^\\s\\\"'<>]+"
                    + ")",
            Pattern.CASE_INSENSITIVE
    );

    private static final WeakHashMap<EditText, Session> SESSIONS =
            new WeakHashMap<>();

    private EditorImagePreview() {
    }

    public static void bind(final EditText editText) {
        if (editText == null) {
            return;
        }

        editText.post(() -> bindOnMain(editText));
    }

    private static void bindOnMain(EditText editText) {
        synchronized (SESSIONS) {
            if (SESSIONS.containsKey(editText)) {
                return;
            }

            Session session = new Session(editText);
            SESSIONS.put(editText, session);
            session.attach(0);
        }
    }

    private static final class Session implements TextWatcher {
        private final EditText editText;
        private final Runnable renderRunnable;

        private LinearLayout previewContainer;
        private List<String> renderedUrls = new ArrayList<>();
        private boolean attached;

        Session(EditText editText) {
            this.editText = editText;
            this.renderRunnable = this::render;
        }

        void attach(final int attempt) {
            if (attached) {
                return;
            }

            Placement placement = findPlacement(editText);

            if (placement == null) {
                if (attempt < 5) {
                    editText.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            attach(attempt + 1);
                        }
                    }, 50L);
                } else {
                    Log.w(
                            TAG,
                            CONTRACT_MARKER
                                    + ": no safe vertical editor host for "
                                    + editText.getClass().getName()
                    );
                }
                return;
            }

            previewContainer = new LinearLayout(editText.getContext());
            previewContainer.setTag(PREVIEW_TAG);
            previewContainer.setOrientation(LinearLayout.VERTICAL);
            previewContainer.setVisibility(View.GONE);
            previewContainer.setImportantForAccessibility(
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO
            );

            LinearLayout.LayoutParams containerParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );

            int insertionIndex = Math.max(
                    0,
                    Math.min(
                            placement.index,
                            placement.parent.getChildCount()
                    )
            );

            placement.parent.addView(
                    previewContainer,
                    insertionIndex,
                    containerParams
            );

            editText.addTextChangedListener(this);
            attached = true;
            scheduleRender();

            Log.d(
                    TAG,
                    CONTRACT_MARKER
                            + ": bound editor="
                            + editText.getClass().getName()
                            + " host="
                            + placement.parent.getClass().getName()
                            + " index="
                            + insertionIndex
            );
        }

        @Override
        public void beforeTextChanged(
                CharSequence value,
                int start,
                int count,
                int after
        ) {
        }

        @Override
        public void onTextChanged(
                CharSequence value,
                int start,
                int before,
                int count
        ) {
            scheduleRender();
        }

        @Override
        public void afterTextChanged(Editable value) {
        }

        private void scheduleRender() {
            editText.removeCallbacks(renderRunnable);
            editText.postDelayed(renderRunnable, UPDATE_DELAY_MS);
        }

        private void render() {
            if (!attached || previewContainer == null) {
                return;
            }

            CharSequence value = editText.getText();
            List<String> urls = extractImageUrls(
                    value == null ? "" : value.toString()
            );

            if (urls.equals(renderedUrls)) {
                return;
            }

            renderedUrls = urls;
            previewContainer.removeAllViews();

            if (urls.isEmpty()) {
                previewContainer.setVisibility(View.GONE);
                Log.d(TAG, CONTRACT_MARKER + ": hidden; no image URL");
                return;
            }

            Context context = editText.getContext();
            int maxHeightPx = resolveMaxPreviewHeightPx(context);

            for (String url : urls) {
                previewContainer.addView(
                        createPreviewCard(context, url, maxHeightPx)
                );
            }

            previewContainer.setVisibility(View.VISIBLE);
            previewContainer.requestLayout();

            Log.d(
                    TAG,
                    CONTRACT_MARKER
                            + ": rendered count="
                            + urls.size()
                            + " urls="
                            + urls
            );
        }
    }

    private static final class Placement {
        final LinearLayout parent;
        final int index;

        Placement(LinearLayout parent, int index) {
            this.parent = parent;
            this.index = index;
        }
    }

    private static Placement findPlacement(EditText editText) {
        View current = editText;
        ViewParent parent = current.getParent();

        while (parent instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) parent;

            if (group instanceof LinearLayout) {
                LinearLayout linearLayout = (LinearLayout) group;

                if (
                        linearLayout.getOrientation() == LinearLayout.VERTICAL
                                && !isTextInputLayout(linearLayout)
                ) {
                    int index = linearLayout.indexOfChild(current);
                    if (index >= 0) {
                        return new Placement(linearLayout, index);
                    }
                }
            }

            if (!(group instanceof View)) {
                break;
            }

            current = (View) group;
            parent = current.getParent();
        }

        return null;
    }

    private static boolean isTextInputLayout(View view) {
        return view != null
                && "com.google.android.material.textfield.TextInputLayout"
                .equals(view.getClass().getName());
    }

    private static View createPreviewCard(
            Context context,
            String url,
            int maxHeightPx
    ) {
        FrameLayout card = new FrameLayout(context);

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        int horizontalMargin = dp(context, 16);
        int verticalMargin = dp(context, 8);

        cardParams.setMargins(
                horizontalMargin,
                verticalMargin,
                horizontalMargin,
                verticalMargin
        );
        card.setLayoutParams(cardParams);

        GradientDrawable background = new GradientDrawable();
        background.setColor(
                resolveThemeColor(
                        context,
                        android.R.attr.colorControlHighlight,
                        0x22808080
                )
        );
        background.setCornerRadius(dp(context, 12));
        card.setBackground(background);
        card.setClipToOutline(true);

        ImageView imageView = new ImageView(context);
        imageView.setAdjustViewBounds(true);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setMinimumHeight(dp(context, 96));
        imageView.setMaxHeight(maxHeightPx);
        imageView.setContentDescription("Editor image preview");

        FrameLayout.LayoutParams imageParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        card.addView(imageView, imageParams);
        loadWithGlide(context, url, imageView);

        return card;
    }

    private static List<String> extractImageUrls(String value) {
        Set<String> unique = new LinkedHashSet<>();

        if (TextUtils.isEmpty(value)) {
            return new ArrayList<>();
        }

        String normalized = value.replace("&amp;", "&");
        Matcher matcher = IMAGE_URL_PATTERN.matcher(normalized);

        while (matcher.find() && unique.size() < MAX_PREVIEWS) {
            String url = cleanUrlTail(matcher.group());
            if (isSupportedImageUrl(url)) {
                unique.add(url);
            }
        }

        return new ArrayList<>(unique);
    }

    private static String cleanUrlTail(String value) {
        if (value == null) {
            return "";
        }

        String result = value;

        while (!result.isEmpty()) {
            char last = result.charAt(result.length() - 1);
            if (
                    last == ')'
                            || last == ']'
                            || last == '}'
                            || last == ','
                            || last == ';'
            ) {
                result = result.substring(0, result.length() - 1);
                continue;
            }
            break;
        }

        return result;
    }

    private static boolean isSupportedImageUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }

        try {
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();

            if (
                    !("http".equalsIgnoreCase(scheme)
                            || "https".equalsIgnoreCase(scheme))
                            || TextUtils.isEmpty(host)
            ) {
                return false;
            }

            String normalizedHost =
                    host.toLowerCase(java.util.Locale.US);

                    return "i.redd.it".equals(normalizedHost)
                    || "preview.redd.it".equals(normalizedHost)
                    || "external-preview.redd.it".equals(normalizedHost)
                    || "i.ibb.co".equals(normalizedHost)
                    || "image.ibb.co".equals(normalizedHost)
                    || "i.imgur.com".equals(normalizedHost);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int resolveMaxPreviewHeightPx(Context context) {
        int screenHeightDp = 800;

        try {
            int configured =
                    context.getResources()
                            .getConfiguration()
                            .screenHeightDp;
            if (configured > 0) {
                screenHeightDp = configured;
            }
        } catch (Throwable ignored) {
        }

        int targetDp = Math.round(screenHeightDp * 0.36f);
        targetDp = Math.max(180, Math.min(320, targetDp));
        return dp(context, targetDp);
    }

    private static int resolveThemeColor(
            Context context,
            int attribute,
            int fallback
    ) {
        try {
            TypedValue value = new TypedValue();

            if (!context.getTheme().resolveAttribute(
                    attribute,
                    value,
                    true
            )) {
                return fallback;
            }

            if (
                    value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                            && value.type <= TypedValue.TYPE_LAST_COLOR_INT
            ) {
                return value.data;
            }

            if (value.resourceId != 0) {
                return context.getResources().getColor(value.resourceId);
            }
        } catch (Throwable ignored) {
        }

        return fallback;
    }

    private static int dp(Context context, int value) {
        return Math.round(
                value
                        * context.getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    private static void loadWithGlide(
            Context context,
            String url,
            ImageView imageView
    ) {
        try {
            MediaNativeBindings.loadImage(null, context, url, imageView);
        } catch (Throwable throwable) {
            Log.w(
                    TAG,
                    CONTRACT_MARKER
                            + ": Glide preview load failed url="
                            + url,
                    throwable
            );
        }
    }

}
