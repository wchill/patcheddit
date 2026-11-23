/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.redditisfun.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.LruCache;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.LinkedHashSet;

public final class InlineVideo implements AudioManager.OnAudioFocusChangeListener {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LinkedHashSet<InlineVideo> PLAYERS = new LinkedHashSet<>();
    private static final LruCache<String, State> STATES = new LruCache<>(128);
    private static final LruCache<String, Frame> FRAMES = new LruCache<String, Frame>(2 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Frame value) { return value.bitmap.getAllocationByteCount(); }
    };
    private static InlineVideo audible;
    private static Handoff handoff;
    private final InlineMedia.Slot slot;
    private final State state;
    private Object player;
    private FrameLayout overlay;
    private TextureView texture;
    private View controls, actions;
    private ViewGroup parent;
    private Button sound;
    private AudioManager audio;
    private boolean muted = true, visible, requested, failed;
    private int retries;
    private long progressTime, lastPosition;
    private final Runnable tick = this::tick;

    private static final class State {
        long position;
        int repeat = 1;
        boolean paused, ended;
    }

    private static final class Frame {
        final Bitmap bitmap;
        final long time = SystemClock.elapsedRealtime();
        Frame(Bitmap bitmap) { this.bitmap = bitmap; }
    }

    private static final class Handoff {
        String url;
        long position, expires;
        int repeat;
        boolean sound;
    }

    InlineVideo(InlineMedia.Slot slot) {
        this.slot = slot;
        State saved = STATES.get(slot.link.getURL());
        state = new State();
        if (saved != null) {
            state.position = saved.position;
            state.repeat = saved.repeat;
            state.paused = saved.paused;
            state.ended = saved.ended;
        }
    }

    private String key() { return slot.link.getURL(); }

    void visible(boolean shown) {
        visible = shown;
        if (!shown) { release(); return; }
        if (failed || !InlineMedia.videos()) return;
        if (player == null && (requested || (!state.paused && InlineMedia.option("autoplay", true)))) start(false);
        if (player != null) {
            place();
            long now = SystemClock.elapsedRealtime();
            long position = NativePlayer.position(player);
            if (position != lastPosition) { lastPosition = position; progressTime = now; }
            if (NativePlayer.failed(player) || (NativePlayer.playing(player) && NativePlayer.state(player) != 4
                    && now - progressTime > 30_000)) {
                boolean resume = NativePlayer.playing(player);
                release();
                if (retries++ == 0) { requested = resume; start(false); }
                else {
                    Log.w(MediaLoader.TAG, "Inline video could not recover; use the original link");
                    failed = true;
                    slot.fail();
                    slot.binding.render();
                }
            }
        }
    }

    void tap() {
        if (!visible || failed) { slot.open(); return; }
        if (player == null) {
            requested = true;
            state.paused = false;
            start(true);
        }
        if (controls != null) {
            controls.setVisibility(View.VISIBLE);
            actions.setVisibility(View.VISIBLE);
        }
    }

    private void start(boolean explicit) {
        TextView view = slot.binding.owner.get();
        if (player != null || view == null || !visible || !slot.binding.current()) return;
        if (PLAYERS.size() == 4) {
            if (!explicit) return;
            PLAYERS.iterator().next().release();
        }
        try {
            parent = (ViewGroup) view.getParent();
            overlay = new FrameLayout(view.getContext());
            texture = new TextureView(view.getContext());
            texture.setOpaque(false);
            overlay.addView(texture, new FrameLayout.LayoutParams(-1, -1));
            MediaLoader.Item item = slot.album.items.get(0);
            player = NativePlayer.create(view.getContext().getApplicationContext(), texture, Uri.parse(item.video),
                    MediaLoader.client(view.getContext()), item.dash);
            NativePlayer.volume(player, 0);
            NativePlayer.repeat(player, state.repeat);
            NativePlayer.seek(player, state.ended ? 0 : state.position);
            NativePlayer.play(player, !state.paused);
            controls = NativePlayer.controls(view.getContext(), player);
            overlay.addView(controls, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout buttons = new LinearLayout(view.getContext());
            buttons.addView(button(view.getContext(), "Restart", () -> {
                NativePlayer.seek(player, 0);
                NativePlayer.play(player, true);
                state.paused = false;
            }));
            sound = button(view.getContext(), "Sound off", () -> {
                if (muted) unmute(); else mute();
            });
            buttons.addView(sound);
            buttons.addView(button(view.getContext(), "Fullscreen", this::fullscreen));
            actions = buttons;
            for (int i = 0; i < buttons.getChildCount(); i++) {
                buttons.getChildAt(i).setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
            }
            overlay.addView(buttons, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP | Gravity.START));
            controls.setVisibility(explicit ? View.VISIBLE : View.GONE);
            actions.setVisibility(controls.getVisibility());
            texture.setOnClickListener(ignored -> tap());
            // A real zero-size child receives input without changing the native body's measurement.
            parent.addView(overlay, new ViewGroup.LayoutParams(0, 0));
            PLAYERS.add(this);
            progressTime = SystemClock.elapsedRealtime();
            lastPosition = state.position;
            view.postDelayed(tick, 1000);
            place();
        } catch (IllegalStateException | IllegalArgumentException exception) {
            Log.w(MediaLoader.TAG, "Could not prepare RIF's inline player", exception);
            release();
            failed = true;
            slot.fail();
            slot.binding.render();
        }
    }

    private void tick() {
        TextView view = slot.binding.owner.get();
        if (player == null || view == null || !slot.binding.current()) return;
        view.invalidate();
        view.postDelayed(tick, 1000);
    }

    private static Button button(Context context, String label, Runnable action) {
        Button button = new Button(context);
        button.setText(label);
        button.setContentDescription(label);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setSingleLine(true);
        button.setEllipsize(android.text.TextUtils.TruncateAt.END);
        button.setOnClickListener(ignored -> action.run());
        return button;
    }

    private void place() {
        TextView view = slot.binding.owner.get();
        if (view == null || overlay == null || !slot.bounds(view)) return;
        int[] body = new int[2], host = new int[2];
        view.getLocationInWindow(body);
        parent.getLocationInWindow(host);
        RectF box = slot.box;
        int left = body[0] - host[0] + parent.getScrollX() + (int) box.left;
        int top = body[1] - host[1] + parent.getScrollY() + (int) box.top;
        int width = Math.max(1, (int) box.width()), height = Math.max(1, (int) box.height());
        overlay.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        overlay.layout(left, top, left + width, top + height);
    }

    private void unmute() {
        if (player == null) return;
        TextView view = slot.binding.owner.get();
        if (view == null) return;
        audio = (AudioManager) view.getContext().getSystemService(Context.AUDIO_SERVICE);
        if (audio == null || audio.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.w(MediaLoader.TAG, "Inline video audio focus was not granted");
            return;
        }
        if (audible != null && audible != this) audible.mute();
        audible = this;
        muted = false;
        NativePlayer.volume(player, 1);
        sound.setText("Sound on");
        sound.setContentDescription(sound.getText());
    }

    private void mute() {
        if (player != null) NativePlayer.volume(player, 0);
        muted = true;
        if (sound != null) {
            sound.setText("Sound off");
            sound.setContentDescription(sound.getText());
        }
        if (audible == this) {
            audible = null;
            if (audio != null) audio.abandonAudioFocus(this);
        }
    }

    @Override public void onAudioFocusChange(int change) {
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) mute();
    }

    private void fullscreen() {
        if (player == null) { slot.open(); return; }
        Handoff next = new Handoff();
        next.url = MediaLoader.videoKey(key());
        next.position = NativePlayer.state(player) == 4 ? 0 : NativePlayer.position(player);
        next.repeat = NativePlayer.repeat(player);
        next.sound = !muted;
        next.expires = SystemClock.elapsedRealtime() + 15_000;
        handoff = next;
        NativePlayer.play(player, false);
        mute();
        slot.open();
    }

    /** Match the native source before consuming anything; unrelated players keep their settings. */
    public static void handoff(Object player, Uri source) {
        Handoff next = handoff;
        if (next == null || player == null || source == null) return;
        if (SystemClock.elapsedRealtime() > next.expires) { handoff = null; return; }
        if (!next.url.equals(MediaLoader.videoKey(source.toString()))) return;
        handoff = null;
        NativePlayer.seek(player, next.position);
        NativePlayer.repeat(player, next.repeat);
        NativePlayer.volume(player, next.sound ? 1 : 0);
    }

    void release() {
        release(false);
    }

    void release(boolean deferOverlayRemoval) {
        if (player == null) return;
        TextView owner = slot.binding.owner.get();
        if (owner != null) owner.removeCallbacks(tick);
        state.position = NativePlayer.position(player);
        state.repeat = NativePlayer.repeat(player);
        state.ended = NativePlayer.state(player) == 4;
        state.paused = !muted || !NativePlayer.playing(player) || state.ended;
        try {
            if (texture != null && texture.isAvailable()) {
                int width = Math.max(1, Math.min(320, texture.getWidth()));
                int height = Math.max(1, Math.min(640, width * texture.getHeight() / Math.max(1, texture.getWidth())));
                Bitmap frame = texture.getBitmap(width, height);
                if (frame != null) FRAMES.put(key(), new Frame(frame));
            }
        } catch (IllegalStateException | OutOfMemoryError exception) {
            Log.w(MediaLoader.TAG, "Could not preserve an inline video's last frame");
        }
        STATES.put(key(), state);
        mute();
        NativePlayer.release(player);
        player = null;
        ViewGroup oldParent = parent;
        View oldOverlay = overlay;
        parent = null;
        overlay = null;
        controls = actions = null;
        texture = null;
        sound = null;
        PLAYERS.remove(this);
        if (oldParent != null && oldOverlay != null) {
            if (deferOverlayRemoval) {
                // Detach dispatch is still iterating the parent's children. Use the main looper
                // rather than View.post so cleanup runs even if the parent never reattaches.
                MAIN.post(() -> {
                    if (oldOverlay.getParent() == oldParent) oldParent.removeView(oldOverlay);
                });
            } else {
                oldParent.removeView(oldOverlay);
            }
        }
    }

    static Bitmap frame(String key) {
        Frame frame = FRAMES.get(key);
        if (frame == null) return null;
        if (SystemClock.elapsedRealtime() - frame.time <= 3000) return frame.bitmap;
        FRAMES.remove(key);
        return null;
    }
}
