/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.youtube;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.FullscreenListener;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.LegacyYouTubePlayerView;

import java.io.InputStream;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;

public final class YouTubeActivity extends Activity implements FullscreenListener {
    private static final String TAG = "PatchedditYouTube";
    private static final String URL = "url";
    private static final String POSITION = "position";
    private FrameLayout root;
    private View inline;
    private View fullscreen;
    private Function0<Unit> leaveFullscreen;
    private LegacyYouTubePlayerView playerView;
    private YouTubePlayer player;
    private YouTubeLink link;
    private float position;

    public static boolean isShortsLink(String url) {
        return YouTubeLink.isShortsLink(url);
    }

    public static boolean openEmbedded(Context context, Uri uri) {
        if (context == null || uri == null || YouTubeLink.parse(uri.toString()) == null) return false;
        Intent intent = new Intent(context, YouTubeActivity.class).putExtra(URL, uri.toString());
        if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        return true;
    }

    public static InputStream openPlayerHtml(View player) {
        Context context = player.getContext();
        return context.getResources().openRawResource(resource(context, "raw", "patcheddit_youtube_player"));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        link = YouTubeLink.parse(getIntent().getStringExtra(URL));
        if (link == null) {
            Log.w(TAG, "Cannot open an unsupported YouTube URL.");
            message("patcheddit_youtube_invalid");
            finish();
            return;
        }
        position = state != null ? state.getFloat(POSITION, link.startSeconds()) : link.startSeconds();

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        LinearLayout content = new LinearLayout(this);
        content.setGravity(Gravity.CENTER);
        content.setOrientation(LinearLayout.VERTICAL);
        inline = content;
        root.addView(content, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        setContentView(root);

        // Use the library's programmatic view without its XML style attributes or AndroidX lifecycle shim.
        playerView = new LegacyYouTubePlayerView(this, this, null, 0);
        content.addView(playerView, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        Button external = new Button(this);
        external.setText(resource(this, "string", "patcheddit_youtube_open_external"));
        external.setOnClickListener(v -> openExternally());
        content.addView(external, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        var options = new IFramePlayerOptions.Builder(this).controls(1).fullscreen(1).build();
        playerView.initialize(new AbstractYouTubePlayerListener() {
            @Override
            public void onReady(YouTubePlayer ready) {
                player = ready;
                if (hasWindowFocus()) ready.loadVideo(link.id(), position);
                else ready.cueVideo(link.id(), position);
            }

            @Override
            public void onCurrentSecond(YouTubePlayer source, float seconds) {
                position = seconds;
            }

            @Override
            public void onError(YouTubePlayer source, PlayerConstants.PlayerError error) {
                Log.w(TAG, "YouTube player reported " + error.name());
                message("patcheddit_youtube_playback_error");
            }
        }, false, options, null);
    }

    @Override
    public void onEnterFullscreen(View view, Function0<Unit> exit) {
        fullscreen = view;
        leaveFullscreen = exit;
        inline.setVisibility(View.GONE);
        root.addView(view, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    @Override
    public void onExitFullscreen() {
        if (fullscreen != null) root.removeView(fullscreen);
        fullscreen = null;
        leaveFullscreen = null;
        inline.setVisibility(View.VISIBLE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    @Override
    public void onBackPressed() {
        if (leaveFullscreen != null) leaveFullscreen.invoke();
        else super.onBackPressed();
    }

    @Override
    protected void onPause() {
        if (player != null) player.pause();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putFloat(POSITION, position);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onDestroy() {
        if (playerView != null) playerView.release();
        super.onDestroy();
    }

    private void openExternally() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link.watchUrl()));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException unavailable) {
            Log.w(TAG, "No application handles YouTube links.", unavailable);
            message("patcheddit_youtube_no_browser");
        }
    }

    private void message(String name) {
        Toast.makeText(this, resource(this, "string", name), Toast.LENGTH_LONG).show();
    }

    private static int resource(Context context, String type, String name) {
        int id = context.getResources().getIdentifier(name, type, context.getPackageName());
        if (id == 0) throw new IllegalStateException("Missing YouTube player resource " + type + "/" + name);
        return id;
    }
}
